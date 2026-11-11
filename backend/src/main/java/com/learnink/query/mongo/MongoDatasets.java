package com.learnink.query.mongo;

import com.learnink.common.ResourceNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Fichiers JSON des jeux de données MongoDB (un fichier par collection), embarqués dans l'application. */
@Component
public class MongoDatasets {

    private final MongoProperties properties;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public MongoDatasets(MongoProperties properties) {
        this.properties = properties;
    }

    public MongoProperties.Dataset dataset(int datasource) {
        MongoProperties.Dataset dataset = properties.datasets().get(datasource);
        if (dataset == null || dataset.name() == null) {
            throw new ResourceNotFoundException("Aucun jeu de données MongoDB pour la source n°" + datasource + ".");
        }
        return dataset;
    }

    /** Fichiers {collection}.json du jeu de données, triés par nom. */
    public List<Resource> files(int datasource) {
        String name = dataset(datasource).name();
        try {
            return Arrays.stream(resolver.getResources("classpath:mongo/" + name + "/*.json"))
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .toList();
        } catch (java.io.FileNotFoundException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> collections(int datasource) {
        return files(datasource).stream().map(MongoDatasets::collectionName).toList();
    }

    public static String collectionName(Resource file) {
        String filename = file.getFilename();
        return filename == null ? "" : filename.substring(0, filename.length() - ".json".length());
    }
}
