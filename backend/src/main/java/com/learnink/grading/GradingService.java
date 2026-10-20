package com.learnink.grading;

import com.learnink.grading.ReferenceResultCache.ReferenceSnapshot;
import com.learnink.grading.quality.QualityIssue;
import com.learnink.grading.quality.QueryQualityAnalyzer;
import com.learnink.query.QueryEngine;
import com.learnink.query.QueryExecutionException;
import com.learnink.query.QueryExecutionStrategy;
import com.learnink.query.QueryExecutionStrategyRegistry;
import com.learnink.query.QueryRejectedException;
import com.learnink.query.QueryResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Note une requête sur 10 :
 * <ul>
 *     <li>6 points pour l'exactitude du résultat (points partiels si le nombre de colonnes / lignes est correct) ;</li>
 *     <li>2 points pour la performance (temps d'exécution comparé à la solution de référence) ;</li>
 *     <li>2 points pour la qualité de l'écriture (SELECT *, jointures implicites, DISTINCT inutile...).</li>
 * </ul>
 * Performance et qualité ne sont évaluées que si le résultat est exact.
 */
@Service
public class GradingService {

    static final double CORRECTNESS_POINTS = 6;
    static final double WRONG_ORDER_POINTS = 4;
    static final double PARTIAL_POINTS = 1;
    static final double PERFORMANCE_POINTS = 2;
    static final double QUALITY_POINTS = 2;

    static final String RESULT_LABEL = "Résultat";
    static final String PERFORMANCE_LABEL = "Performance";
    static final String QUALITY_LABEL = "Qualité de la requête";
    static final String NOT_EVALUATED = "Non évalué tant que le résultat n'est pas exact.";

    private final QueryExecutionStrategyRegistry strategies;
    private final ResultComparator comparator;
    private final PerformanceEvaluator performanceEvaluator;
    private final ReferenceResultCache referenceCache;
    private final Map<QueryEngine, QueryQualityAnalyzer> qualityAnalyzers;
    private final ExecutionErrorAdvisor errorAdvisor;

    public GradingService(QueryExecutionStrategyRegistry strategies, ResultComparator comparator,
                          PerformanceEvaluator performanceEvaluator, ReferenceResultCache referenceCache,
                          List<QueryQualityAnalyzer> qualityAnalyzers, ExecutionErrorAdvisor errorAdvisor) {
        this.errorAdvisor = errorAdvisor;
        this.strategies = strategies;
        this.comparator = comparator;
        this.performanceEvaluator = performanceEvaluator;
        this.referenceCache = referenceCache;
        this.qualityAnalyzers = qualityAnalyzers.stream()
                .collect(Collectors.toMap(QueryQualityAnalyzer::engine, Function.identity()));
    }

    public GradingResult grade(GradingRequest request) {
        QueryExecutionStrategy strategy = strategies.get(request.engine());
        QueryResult studentResult;
        try {
            studentResult = strategy.execute(request.studentQuery(), request.target());
        } catch (QueryRejectedException | QueryExecutionException e) {
            return GradingResult.failed(e.getMessage(), errorAdvisor.adviceFor(e.getMessage()));
        }

        ReferenceSnapshot reference = referenceCache.get(strategy, request.referenceQuery(), request.target());
        ComparisonOutcome comparison = comparator.compare(reference.result(), studentResult, request.orderMatters());

        List<ScoreCriterion> criteria = new ArrayList<>();
        List<String> hints = new ArrayList<>(comparison.hints());
        criteria.add(correctness(comparison));
        if (comparison.identical()) {
            criteria.add(performanceEvaluator.evaluate(strategy, request.studentQuery(), request.target(),
                    studentResult.executionTimeNanos(), reference.bestExecutionNanos(), PERFORMANCE_POINTS));
            criteria.add(quality(request, hints));
        } else {
            criteria.add(new ScoreCriterion(PERFORMANCE_LABEL, 0, PERFORMANCE_POINTS, NOT_EVALUATED));
            criteria.add(new ScoreCriterion(QUALITY_LABEL, 0, QUALITY_POINTS, NOT_EVALUATED));
        }

        double total = criteria.stream().mapToDouble(ScoreCriterion::points).sum();
        ScoreReport report = new ScoreReport(roundToHalf(total), comparison.identical(), criteria, hints);
        return new GradingResult(report, studentResult, null);
    }

    private static ScoreCriterion correctness(ComparisonOutcome comparison) {
        if (comparison.identical()) {
            return new ScoreCriterion(RESULT_LABEL, CORRECTNESS_POINTS, CORRECTNESS_POINTS,
                    "Le résultat correspond exactement à celui attendu.");
        }
        if (comparison.sameRowsInWrongOrder()) {
            return new ScoreCriterion(RESULT_LABEL, WRONG_ORDER_POINTS, CORRECTNESS_POINTS,
                    "Les bonnes lignes, mais pas dans le bon ordre.");
        }
        double points = (comparison.sameColumnCount() ? PARTIAL_POINTS : 0)
                + (comparison.sameRowCount() ? PARTIAL_POINTS : 0);
        return new ScoreCriterion(RESULT_LABEL, points, CORRECTNESS_POINTS, "Le résultat ne correspond pas à celui attendu.");
    }

    private ScoreCriterion quality(GradingRequest request, List<String> hints) {
        QueryQualityAnalyzer analyzer = qualityAnalyzers.get(request.engine());
        List<QualityIssue> issues = analyzer == null
                ? List.of()
                : analyzer.analyze(request.studentQuery(), request.referenceQuery());
        issues.forEach(issue -> hints.add(issue.message()));
        double penalty = issues.stream().mapToDouble(QualityIssue::penalty).sum();
        double points = Math.max(0, QUALITY_POINTS - penalty);
        String comment = issues.isEmpty()
                ? "Requête claire et bien écrite."
                : issues.size() + " point(s) d'amélioration détecté(s).";
        return new ScoreCriterion(QUALITY_LABEL, points, QUALITY_POINTS, comment);
    }

    private static double roundToHalf(double value) {
        return Math.min(ScoreReport.MAX_SCORE, Math.round(value * 2) / 2.0);
    }
}
