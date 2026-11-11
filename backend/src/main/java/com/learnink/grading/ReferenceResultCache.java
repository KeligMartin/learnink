package com.learnink.grading;

import com.learnink.query.QueryExecutionStrategy;
import com.learnink.query.QueryResult;
import com.learnink.query.QueryTarget;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Garde en mémoire le résultat et le temps d'exécution des solutions de référence :
 * les bases interrogées étant en lecture seule, ils ne changent pas.
 */
@Component
public class ReferenceResultCache {

    static final int TIMING_RUNS = 3;

    public record ReferenceSnapshot(QueryResult result, long bestExecutionNanos) {
    }

    private final ConcurrentMap<String, ReferenceSnapshot> cache = new ConcurrentHashMap<>();

    /**
     * La base de référence fait partie de la clé : chaque base a ses propres données et ses propres temps
     * (base partagée d'une source de données, ou base personnelle d'un étudiant).
     */
    public ReferenceSnapshot get(QueryExecutionStrategy strategy, String referenceQuery, QueryTarget target) {
        QueryTarget referenceTarget = strategy.referenceTarget(target);
        return cache.computeIfAbsent(key(strategy, referenceTarget) + referenceQuery,
                key -> measure(strategy, referenceQuery, referenceTarget));
    }

    /** Oublie les résultats calculés sur la base personnelle d'un utilisateur (base modifiée ou remplacée). */
    public void evictUser(Long userId) {
        String marker = ":u" + userId + ":";
        cache.keySet().removeIf(key -> key.contains(marker));
    }

    private static String key(QueryExecutionStrategy strategy, QueryTarget target) {
        return strategy.engine() + ":" + target.datasource() + ":" + (target.userId() == null ? "s" : "u" + target.userId()) + ":";
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
