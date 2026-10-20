package com.learnink.grading;

import com.learnink.query.QueryExecutionStrategy;
import com.learnink.query.QueryTarget;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Évalue l'efficacité d'une requête correcte en comparant son temps d'exécution à celui de la solution de référence.
 */
@Component
public class PerformanceEvaluator {

    /** En dessous de ce seuil, les écarts de temps relèvent du bruit de mesure. */
    static final long NOISE_FLOOR_NANOS = 5_000_000;
    static final int EXTRA_RUNS = 2;
    static final double FAST_RATIO = 1.5;
    static final double ACCEPTABLE_RATIO = 3;

    public ScoreCriterion evaluate(QueryExecutionStrategy strategy, String query, QueryTarget target, long firstRunNanos,
                                   long referenceNanos, double maxPoints) {
        long best = firstRunNanos;
        for (int i = 0; i < EXTRA_RUNS; i++) {
            best = Math.min(best, strategy.execute(query, target).executionTimeNanos());
        }
        double ratio = (double) best / Math.max(referenceNanos, 1);
        String timing = String.format(Locale.FRANCE, "%.1f ms (référence : %.1f ms)",
                best / 1_000_000.0, referenceNanos / 1_000_000.0);

        if (best <= NOISE_FLOOR_NANOS || ratio <= FAST_RATIO) {
            return new ScoreCriterion(GradingService.PERFORMANCE_LABEL, maxPoints, maxPoints,
                    "Temps d'exécution comparable à la solution de référence : " + timing + ".");
        }
        String slower = String.format(Locale.FRANCE, "Requête %.1f fois plus lente que la référence : %s.", ratio, timing);
        double points = ratio <= ACCEPTABLE_RATIO ? maxPoints / 2 : 0;
        return new ScoreCriterion(GradingService.PERFORMANCE_LABEL, points, maxPoints, slower);
    }
}
