package com.learnink.query.mongo;

import com.learnink.query.QueryEngine;
import com.learnink.query.QueryExecutionException;
import com.learnink.query.QueryExecutionStrategy;
import com.learnink.query.QueryResult;
import com.learnink.query.QueryTarget;
import com.learnink.query.SchemaDescription;
import com.learnink.query.sql.SandboxProperties;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.CountOptions;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Stratégie MongoDB : la requête (syntaxe mongosh) est analysée puis traduite en appels du pilote Java ;
 * aucun code n'est exécuté. Chaque étudiant interroge sa propre base (ou la base partagée de sa source de données).
 */
@Component
public class MongoQueryExecutionStrategy implements QueryExecutionStrategy {

    private static final int SCHEMA_SAMPLE = 20;

    private final MongoQueryParser parser;
    private final MongoTargets targets;
    private final MongoClientPool clients;
    private final MongoProperties properties;
    private final SandboxProperties limits;

    public MongoQueryExecutionStrategy(MongoQueryParser parser, MongoTargets targets, MongoClientPool clients,
                                       MongoProperties properties, SandboxProperties limits) {
        this.parser = parser;
        this.targets = targets;
        this.clients = clients;
        this.properties = properties;
        this.limits = limits;
    }

    @Override
    public QueryEngine engine() {
        return QueryEngine.MONGODB;
    }

    /** Toujours disponible : chaque étudiant y branche sa propre base. */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public QueryTarget referenceTarget(QueryTarget target) {
        return targets.usesPersonalDatabase(target) ? target : QueryTarget.system(target.datasource());
    }

    @Override
    public QueryResult execute(String query, QueryTarget target) {
        MongoQuery parsed = parser.parse(query);
        MongoTargets.Location location = targets.resolve(target);
        MongoDatabase database = clients.get(location.uri(), !location.personal()).getDatabase(location.database());
        long start = System.nanoTime();
        try {
            List<Document> documents = new ArrayList<>();
            boolean truncated = run(parsed, database.getCollection(parsed.collection()), documents);
            long elapsed = System.nanoTime() - start;
            if (documents.isEmpty()) {
                checkCollectionExists(database, parsed.collection());
            }
            DocumentConverter.Table table = DocumentConverter.toTable(documents);
            return new QueryResult(table.columns(), table.rows(), truncated, elapsed, table.documents());
        } catch (MongoException e) {
            throw translate(e, location);
        }
    }

    private boolean run(MongoQuery query, MongoCollection<Document> collection, List<Document> out) {
        long timeout = properties.timeoutSeconds();
        return switch (query) {
            case MongoQuery.Find find -> {
                var iterable = collection.find(find.filter()).maxTime(timeout, TimeUnit.SECONDS);
                if (find.projection() != null) {
                    iterable = iterable.projection(find.projection());
                }
                if (find.sort() != null) {
                    iterable = iterable.sort(find.sort());
                }
                if (find.skip() != null) {
                    iterable = iterable.skip(find.skip());
                }
                if (find.limit() != null) {
                    iterable = iterable.limit(find.limit());
                }
                yield collect(iterable, out);
            }
            case MongoQuery.Count count -> {
                long n = collection.countDocuments(count.filter(), new CountOptions().maxTime(timeout, TimeUnit.SECONDS));
                out.add(new Document("count", n));
                yield false;
            }
            case MongoQuery.Aggregate aggregate -> collect(collection.aggregate(aggregate.pipeline())
                    .maxTime(timeout, TimeUnit.SECONDS).allowDiskUse(false), out);
            case MongoQuery.Distinct distinct -> {
                List<BsonValue> values = new ArrayList<>();
                collection.distinct(distinct.field(), distinct.filter(), BsonValue.class)
                        .maxTime(timeout, TimeUnit.SECONDS).into(values);
                for (BsonValue value : values) {
                    if (out.size() == limits.maxComparedRows()) {
                        yield true;
                    }
                    out.add(Document.parse(new BsonDocument(distinct.field().replace('.', '_'), value).toJson()));
                }
                yield false;
            }
        };
    }

    private boolean collect(MongoIterable<Document> iterable, List<Document> out) {
        try (MongoCursor<Document> cursor = iterable.batchSize(500).iterator()) {
            while (cursor.hasNext()) {
                if (out.size() == limits.maxComparedRows()) {
                    return true;
                }
                out.add(cursor.next());
            }
        }
        return false;
    }

    private static void checkCollectionExists(MongoDatabase database, String collection) {
        List<String> names = database.listCollectionNames().into(new ArrayList<>());
        if (!names.contains(collection)) {
            names.removeIf(name -> name.startsWith("_") || name.startsWith("system."));
            names.sort(Comparator.naturalOrder());
            throw new QueryExecutionException("La collection « " + collection + " » n'existe pas dans la base"
                    + (names.isEmpty() ? " : elle est vide, importez d'abord le jeu de données."
                    : ". Collections disponibles : " + String.join(", ", names) + "."));
        }
    }

    private QueryExecutionException translate(MongoException e, MongoTargets.Location location) {
        String who = location.personal() ? "votre base MongoDB" : "la base MongoDB de la plateforme";
        if (e instanceof MongoExecutionTimeoutException) {
            return new QueryExecutionException("La requête a dépassé le temps maximal autorisé ("
                    + properties.timeoutSeconds() + " s).", e);
        }
        if (e instanceof MongoSecurityException) {
            return new QueryExecutionException("Connexion refusée par " + who + " : vérifiez l'utilisateur et le mot de "
                    + "passe de la chaîne de connexion.", e);
        }
        if (e instanceof MongoTimeoutException || e instanceof MongoSocketException) {
            return new QueryExecutionException("Impossible de joindre " + who + " : vérifiez l'adresse et, sur Atlas, "
                    + "que l'accès réseau (Network Access) autorise toutes les adresses (0.0.0.0/0).", e);
        }
        if (e instanceof MongoCommandException command) {
            return new QueryExecutionException(command.getErrorMessage(), e);
        }
        return new QueryExecutionException(e.getMessage(), e);
    }

    // --------------------------------------------------------------------------------------------------------- schéma

    /**
     * Collections de la base et chemins de leurs champs (sur un échantillon de documents), avec leur type.
     * Un champ « xxx_id » est présenté comme une référence vers la collection « xxx » si elle existe.
     */
    @Override
    public SchemaDescription describeSchema(QueryTarget target) {
        MongoTargets.Location location = targets.resolve(target);
        MongoDatabase database = clients.get(location.uri(), !location.personal()).getDatabase(location.database());
        try {
            List<String> names = database.listCollectionNames().into(new ArrayList<>());
            names.removeIf(name -> name.startsWith("_") || name.startsWith("system."));
            names.sort(Comparator.naturalOrder());
            List<SchemaDescription.Table> tables = new ArrayList<>();
            for (String name : names) {
                Map<String, String> fields = new LinkedHashMap<>();
                for (Document document : database.getCollection(name).find().limit(SCHEMA_SAMPLE)) {
                    collectFields("", document, fields);
                }
                List<SchemaDescription.Column> columns = new ArrayList<>();
                fields.forEach((path, type) -> columns.add(new SchemaDescription.Column(path, type, "_id".equals(path),
                        reference(path, names))));
                tables.add(new SchemaDescription.Table(name, columns));
            }
            return new SchemaDescription(tables);
        } catch (MongoException e) {
            throw translate(e, location);
        }
    }

    private static void collectFields(String prefix, Map<?, ?> document, Map<String, String> fields) {
        for (Map.Entry<?, ?> entry : document.entrySet()) {
            String path = prefix + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                fields.putIfAbsent(path, "document");
                collectFields(path + ".", nested, fields);
            } else if (value instanceof List<?> list) {
                boolean documents = !list.isEmpty() && list.getFirst() instanceof Map<?, ?>;
                if (list.isEmpty()) {
                    fields.putIfAbsent(path, "tableau");
                } else {
                    fields.put(path, "tableau de " + (documents ? "documents" : typeOf(list.getFirst())));
                }
                if (documents) {
                    Map<String, String> elementFields = new TreeMap<>();
                    list.stream().filter(Map.class::isInstance).limit(5)
                            .forEach(element -> collectFields(path + ".", (Map<?, ?>) element, elementFields));
                    elementFields.forEach(fields::putIfAbsent);
                }
            } else if (value != null) {
                fields.put(path, typeOf(value));
            } else {
                fields.putIfAbsent(path, "null");
            }
        }
    }

    private static String typeOf(Object value) {
        return switch (value) {
            case null -> "null";
            case String s -> "texte";
            case Integer i -> "entier";
            case Long l -> "entier";
            case Number n -> "décimal";
            case Boolean b -> "booléen";
            case java.util.Date d -> "date";
            case org.bson.types.ObjectId id -> "ObjectId";
            default -> value.getClass().getSimpleName();
        };
    }

    private static String reference(String path, List<String> collections) {
        String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        if (field.endsWith("_id") && !field.equals("_id")) {
            String collection = field.substring(0, field.length() - 3);
            if (collections.contains(collection)) {
                return collection + "._id";
            }
        }
        return null;
    }
}
