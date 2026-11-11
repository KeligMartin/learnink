package com.learnink.profile;

import com.learnink.common.InvalidRequestException;
import com.learnink.common.ResourceNotFoundException;
import com.learnink.grading.ReferenceResultCache;
import com.learnink.query.mongo.MongoClientPool;
import com.learnink.query.mongo.MongoDatasets;
import com.learnink.query.mongo.MongoProperties;
import com.learnink.query.mongo.MongoUriValidator;
import com.learnink.user.User;
import com.learnink.user.UserRepository;
import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Base MongoDB personnelle d'un étudiant : enregistrement, vérification de la connexion et du jeu de données. */
@Service
public class MongoProfileService {

    /**
     * @param configured         vrai si une base personnelle est enregistrée
     * @param uri                chaîne de connexion, mot de passe masqué
     * @param database           base interrogée
     * @param dataset            jeu de données attendu (résidence, transport)
     * @param expectedCollections collections attendues dans la base
     * @param missingCollections collections absentes ou vides lors de la dernière vérification (null si non vérifié)
     */
    public record MongoProfileDto(boolean configured, String uri, String database, String dataset,
                                  List<String> expectedCollections, List<String> missingCollections,
                                  boolean sharedDatabaseAvailable) {
    }

    private final UserRepository userRepository;
    private final MongoUriValidator validator;
    private final MongoClientPool clients;
    private final MongoDatasets datasets;
    private final MongoProperties properties;
    private final ReferenceResultCache referenceCache;

    public MongoProfileService(UserRepository userRepository, MongoUriValidator validator, MongoClientPool clients,
                               MongoDatasets datasets, MongoProperties properties, ReferenceResultCache referenceCache) {
        this.userRepository = userRepository;
        this.validator = validator;
        this.clients = clients;
        this.datasets = datasets;
        this.properties = properties;
        this.referenceCache = referenceCache;
    }

    public MongoProfileDto status(Long userId) {
        User user = find(userId);
        String uri = user.getMongoUri();
        return dto(user, uri, null);
    }

    /** Teste la connexion avant d'enregistrer : la base doit répondre et contenir les collections du jeu de données. */
    @Transactional
    public MongoProfileDto save(Long userId, String uri) {
        User user = find(userId);
        ConnectionString connection = validator.validate(uri);
        String value = uri.strip();
        List<String> missing = check(value, databaseOf(connection), datasets.collections(user.getDatasource()));
        String previous = user.getMongoUri();
        user.configureMongo(value);
        if (previous != null && !previous.equals(value)) {
            clients.close(previous);
        }
        referenceCache.evictUser(userId);
        return dto(user, value, missing);
    }

    /** Vérifie à nouveau la base enregistrée (après un import, par exemple). */
    public MongoProfileDto verify(Long userId) {
        User user = find(userId);
        if (user.getMongoUri() == null) {
            throw new InvalidRequestException("Aucune base MongoDB n'est enregistrée.");
        }
        ConnectionString connection = new ConnectionString(user.getMongoUri());
        List<String> missing = check(user.getMongoUri(), databaseOf(connection), datasets.collections(user.getDatasource()));
        referenceCache.evictUser(userId);
        return dto(user, user.getMongoUri(), missing);
    }

    @Transactional
    public MongoProfileDto remove(Long userId) {
        User user = find(userId);
        if (user.getMongoUri() != null) {
            clients.close(user.getMongoUri());
        }
        user.configureMongo(null);
        referenceCache.evictUser(userId);
        return dto(user, null, null);
    }

    private List<String> check(String uri, String databaseName, List<String> expected) {
        try (MongoClient client = clients.createTemporary(uri)) {
            MongoDatabase database = client.getDatabase(databaseName);
            database.runCommand(new Document("ping", 1));
            List<String> present = database.listCollectionNames().into(new ArrayList<>());
            List<String> missing = new ArrayList<>();
            for (String collection : expected) {
                if (!present.contains(collection) || database.getCollection(collection).estimatedDocumentCount() == 0) {
                    missing.add(collection);
                }
            }
            return missing;
        } catch (MongoSecurityException e) {
            throw new InvalidRequestException("Connexion refusée : vérifiez l'utilisateur et le mot de passe "
                    + "(ceux de l'utilisateur de la base, dans Database Access sur Atlas).");
        } catch (MongoTimeoutException e) {
            throw new InvalidRequestException("Base injoignable : vérifiez l'adresse du cluster et, sur Atlas, que "
                    + "Network Access autorise toutes les adresses (0.0.0.0/0).");
        } catch (MongoException e) {
            throw new InvalidRequestException("Erreur MongoDB : " + e.getMessage());
        }
    }

    private String databaseOf(ConnectionString connection) {
        String database = connection.getDatabase();
        return database == null || database.isBlank() ? properties.database() : database;
    }

    private MongoProfileDto dto(User user, String uri, List<String> missing) {
        int datasource = user.getDatasource();
        String datasetName = properties.datasets().containsKey(datasource) ? datasets.dataset(datasource).name() : null;
        List<String> expected = datasetName == null ? List.of() : datasets.collections(datasource);
        boolean shared = properties.datasets().containsKey(datasource)
                && properties.datasets().get(datasource).hasSharedDatabase();
        return new MongoProfileDto(uri != null, MongoUriValidator.mask(uri),
                uri == null ? properties.database() : databaseOf(new ConnectionString(uri)), datasetName, expected,
                missing, shared);
    }

    private User find(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable."));
    }
}
