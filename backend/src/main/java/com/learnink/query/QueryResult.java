package com.learnink.query;

import java.util.List;

/**
 * Résultat tabulaire d'une requête. Les valeurs sont déjà converties en types sérialisables en JSON.
 *
 * @param truncated vrai si le résultat dépasse la limite de lignes lues
 */
public record QueryResult(List<String> columns, List<List<Object>> rows, boolean truncated, long executionTimeNanos) {

    public int rowCount() {
        return rows.size();
    }
}
