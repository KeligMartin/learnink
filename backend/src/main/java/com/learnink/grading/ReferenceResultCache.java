package com.learnink.grading;

import com.learnink.query.QueryExecutionStrategy;
import com.learnink.query.QueryResult;
import com.learnink.query.QueryTarget;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Garde en mémoire le résultat et le temps d'exécution des solutions de référence :
 * la base bac à sable étant en lecture seule, ils ne changent pas.
 */
@Component
public class ReferenceResultCache {

    static final int TIMING_RUNS = 3;

    public record ReferenceSnapshot(QueryResult result, long bestExecutionNanos) {
    }

    private final ConcurrentMap<String, ReferenceSnapshot> cache = new ConcurrentHashMap<>();

    /** La source de données fait partie de la clé : chaque base a ses propres données et ses propres temps. */
    public ReferenceSnapshot get(QueryExecutionStrategy strategy, String referenceQuery, QueryTarget target) {
        return cache.computeIfAbsent(strategy.engine() + ":" + target.datasource() + ":" + referenceQuery,
                key -> measure(strategy, referenceQuery, QueryTarget.system(target.datasource())));
    }

    private static ReferenceSnapshot measure(QueryExecutionStrategy strategy, String referenceQuery, QueryTarget target) {
        QueryResult result = strategy.execute(referenceQuery, target);
        long best = result.executionTimeNanos();
        for (int i = 1; i < TIMING_RUNS; i++) {
            best = Math.min(best, strategy.execute(referenceQuery, target).executionTimeNanos());
        }
        return new ReferenceSnapshot(result, best);
    }
}
