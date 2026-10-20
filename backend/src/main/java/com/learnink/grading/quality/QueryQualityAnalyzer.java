package com.learnink.grading.quality;

import com.learnink.query.QueryEngine;

import java.util.List;

/**
 * Analyse statique de la qualité d'une requête, propre à chaque moteur.
 */
public interface QueryQualityAnalyzer {

    QueryEngine engine();

    List<QualityIssue> analyze(String studentQuery, String referenceQuery);
}
