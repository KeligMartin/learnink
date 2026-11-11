package com.learnink.query;

import java.util.List;

/**
 * Résultat tabulaire d'une requête. Les valeurs sont déjà converties en types sérialisables en JSON.
 *
 * @param truncated vrai si le résultat dépasse la limite de lignes lues
 * @param documents documents bruts au format JSON (moteurs documentaires), affichés tels quels ; null sinon
 */
public record QueryResult(List<String> columns, List<List<Object>> rows, boolean truncated, long executionTimeNanos,
                          List<String> documents) {

    public QueryResult(List<String> columns, List<List<Object>> rows, boolean truncated, long executionTimeNanos) {
        this(columns, rows, truncated, executionTimeNanos, null);
    }

    public int rowCount() {
        return rows.size();
    }
}
