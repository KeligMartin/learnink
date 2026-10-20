package com.learnink.query.sql;

import com.learnink.query.EngineUnavailableException;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bases bac à sable configurées, indexées par numéro de source de données (champ "datasource" des utilisateurs).
 * Une entrée sans URL est ignorée : la seconde base est facultative.
 */
public class SandboxDataSources implements AutoCloseable {

    private final Map<Integer, SandboxDataSource> dataSources = new TreeMap<>();

    public SandboxDataSources(SandboxProperties properties) {
        properties.configuredDatasources()
                .forEach((number, datasource) -> dataSources.put(number, new SandboxDataSource(number, datasource)));
        if (dataSources.isEmpty()) {
            throw new IllegalStateException("Aucune base bac à sable configurée (learnink.sandbox.datasources)");
        }
    }

    public SandboxDataSource get(int number) {
        SandboxDataSource dataSource = dataSources.get(number);
        if (dataSource == null) {
            throw new EngineUnavailableException("La source de données n°" + number
                    + " n'est pas configurée : contactez votre enseignant.");
        }
        return dataSource;
    }

    public Set<Integer> numbers() {
        return Collections.unmodifiableSet(dataSources.keySet());
    }

    public boolean contains(int number) {
        return dataSources.containsKey(number);
    }

    @Override
    public void close() {
        dataSources.values().forEach(SandboxDataSource::close);
    }
}
