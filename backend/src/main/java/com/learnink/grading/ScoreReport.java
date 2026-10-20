package com.learnink.grading;

import java.util.List;

/**
 * Note sur 10 et détail par critère, affichés en direct à l'étudiant.
 */
public record ScoreReport(double score, boolean correct, List<ScoreCriterion> criteria, List<String> hints) {

    public static final double MAX_SCORE = 10;
}
