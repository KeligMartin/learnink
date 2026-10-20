package com.learnink.grading;

import com.learnink.query.QueryResult;

import java.util.List;

/**
 * @param result résultat de la requête étudiante, absent si elle n'a pas pu être exécutée
 * @param error  message d'erreur de validation ou d'exécution
 */
public record GradingResult(ScoreReport report, QueryResult result, String error) {

    static GradingResult failed(String error, List<String> hints) {
        List<ScoreCriterion> criteria = List.of(
                new ScoreCriterion(GradingService.RESULT_LABEL, 0, GradingService.CORRECTNESS_POINTS, error),
                new ScoreCriterion(GradingService.PERFORMANCE_LABEL, 0, GradingService.PERFORMANCE_POINTS,
                        GradingService.NOT_EVALUATED),
                new ScoreCriterion(GradingService.QUALITY_LABEL, 0, GradingService.QUALITY_POINTS,
                        GradingService.NOT_EVALUATED));
        return new GradingResult(new ScoreReport(0, false, criteria, hints), null, error);
    }
}
