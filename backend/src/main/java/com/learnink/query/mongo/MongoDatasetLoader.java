package com.learnink.query.mongo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnink.startup.StartupOrder;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Charge les jeux de données dans les bases MongoDB partagées de la plateforme (si configurées), au démarrage,
 * uniquement quand les fichiers JSON ont changé : l'empreinte du dernier chargement est conservée dans la collection
 * {@value #STATE_COLLECTION} de la base elle-même.
 */
@Component
@Order(StartupOrder.SANDBOX + 1)
public class MongoDatasetLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoDatasetLoader.class);
    static final String STATE_COLLECTION = "_learnink";
    private static final int BATCH = 1000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MongoProperties properties;
    private final MongoDatasets datasets;
    private final MongoClientPool clients;

    public MongoDatasetLoader(MongoProperties properties, MongoDatasets datasets, MongoClientPool clients) {
        this.properties = properties;
        this.datasets = datasets;
        this.clients = clients;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        for (Map.Entry<Integer, MongoProperties.Dataset> entry : properties.datasets().entrySet()) {
            if (entry.getValue().hasSharedDatabase()) {
                try {
                    load(entry.getKey(), entry.getValue());
                } catch (RuntimeException e) {
                    // La base partagée est facultative : son absence ne doit pas empêcher la plateforme de démarrer
                    log.error("Chargement de la base MongoDB partagée de la source n°{} impossible : {}", entry.getKey(),
                            e.getMessage());
                }
            }
        }
    }

    void load(int datasource, MongoProperties.Dataset dataset) throws IOException {
        List<Resource> files = datasets.files(datasource);
        if (files.isEmpty()) {
            log.warn("Aucun fichier JSON pour le jeu de données MongoDB « {} » (source n°{})", dataset.name(), datasource);
            return;
        }
        String checksum = checksum(files);
        String databaseName = MongoTargets.sharedDatabase(dataset);
        MongoDatabase database = clients.get(dataset.uri(), true).getDatabase(databaseName);
        MongoCollection<Document> state = database.getCollection(STATE_COLLECTION);
        Document current = state.find(new Document("_id", "dataset")).first();
        if (current != null && checksum.equals(current.getString("checksum"))) {
            log.info("Base MongoDB partagée de la source n°{} ({}) à jour", datasource, databaseName);
            return;
        }
        long start = System.currentTimeMillis();
        int total = 0;
        for (Resource file : files) {
            MongoCollection<Document> collection = database.getCollection(MongoDatasets.collectionName(file));
            collection.drop();
            List<Document> batch = new ArrayList<>(BATCH);
            int[] count = {0};
            readDocuments(file, document -> {
                batch.add(document);
                count[0]++;
                if (batch.size() == BATCH) {
                    collection.insertMany(batch);
                    batch.clear();
                }
            });
            if (!batch.isEmpty()) {
                collection.insertMany(batch);
            }
            total += count[0];
        }
        state.replaceOne(new Document("_id", "dataset"),
                new Document("_id", "dataset").append("checksum", checksum).append("name", dataset.name()),
                new ReplaceOptions().upsert(true));
        log.info("Base MongoDB partagée de la source n°{} ({}) chargée : {} documents en {} ms", datasource, databaseName,
                total, System.currentTimeMillis() - start);
    }

    /** Lit un fichier JSON (tableau de documents en Extended JSON) document par document. */
    static void readDocuments(Resource file, Consumer<Document> consumer) throws IOException {
        try (InputStream input = file.getInputStream(); JsonParser parser = JSON.getFactory().createParser(input)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException(file.getFilename() + " : un tableau de documents est attendu");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                consumer.accept(Document.parse(JSON.readTree(parser).toString()));
            }
        }
    }

    private static String checksum(List<Resource> files) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Resource file : files) {
                digest.update(String.valueOf(file.getFilename()).getBytes(StandardCharsets.UTF_8));
                try (InputStream input = file.getInputStream()) {
                    digest.update(input.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
