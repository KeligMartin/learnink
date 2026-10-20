package com.learnink.grading.quality.sql;

import com.learnink.grading.quality.QualityIssue;

import java.util.Optional;

/**
 * Règle de qualité SQL. Chaque règle est un bean indépendant : en ajouter une ne modifie pas l'analyseur.
 * Une règle ne pénalise jamais une construction également présente dans la solution de référence.
 */
public interface SqlQualityRule {

    Optional<QualityIssue> evaluate(SqlAnalysisContext context);
}
