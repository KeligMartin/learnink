package com.learnink.grading;

import java.util.List;

/**
 * Résultat de la comparaison entre le résultat attendu et celui de l'étudiant.
 *
 * @param sameRowsInWrongOrder les lignes sont correctes mais l'ordre demandé n'est pas respecté
 */
public record ComparisonOutcome(boolean identical, boolean sameColumnCount, boolean sameRowCount,
                                boolean sameRowsInWrongOrder, List<String> hints) {
}
