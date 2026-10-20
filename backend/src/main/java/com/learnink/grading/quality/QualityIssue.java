package com.learnink.grading.quality;

/**
 * Défaut d'écriture détecté dans une requête, avec la pénalité associée (en points).
 */
public record QualityIssue(String message, double penalty) {
}
