package com.learnink.query.firestore;

import com.learnink.query.EngineUnavailableException;
import com.learnink.query.QueryEngine;
import com.learnink.query.QueryExecutionStrategy;
import com.learnink.query.QueryResult;
import com.learnink.query.QueryTarget;
import com.learnink.query.SchemaDescription;
import org.springframework.stereotype.Component;

/**
 * Stratégie Firestore : un projet Firestore sera provisionné pour chaque étudiant.
 * En attente de la mise en place de la base de données, le moteur est déclaré indisponible.
 */
@Component
public class FirestoreQueryExecutionStrategy implements QueryExecutionStrategy {

    @Override
    public QueryEngine engine() {
        return QueryEngine.FIRESTORE;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public QueryResult execute(String query, QueryTarget target) {
        throw new EngineUnavailableException("Le moteur Firestore n'est pas encore configuré.");
    }

    @Override
    public SchemaDescription describeSchema(QueryTarget target) {
        throw new EngineUnavailableException("Le moteur Firestore n'est pas encore configuré.");
    }
}
