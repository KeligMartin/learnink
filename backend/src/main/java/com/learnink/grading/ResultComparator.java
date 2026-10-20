package com.learnink.grading;

import com.learnink.query.QueryResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compare deux résultats tabulaires. Les noms de colonnes sont ignorés (seules les positions comptent)
 * et les nombres sont comparés par valeur (2 = 2.0 = 2.00).
 */
@Component
public class ResultComparator {

    private static final int NUMERIC_SCALE = 4;
    private static final String NULL_MARKER = "∅";
    private static final String SEPARATOR = "\u0001";

    public ComparisonOutcome compare(QueryResult expected, QueryResult actual, boolean orderMatters) {
        int expectedColumns = expected.columns().size();
        int actualColumns = actual.columns().size();
        boolean sameRowCount = !actual.truncated() && expected.rowCount() == actual.rowCount();

        if (expectedColumns != actualColumns) {
            return new ComparisonOutcome(false, false, sameRowCount, false, List.of(
                    "La solution attendue renvoie " + plural(expectedColumns, "colonne")
                            + ", votre requête en renvoie " + actualColumns + "."));
        }

        List<String> expectedRows = rowKeys(expected);
        List<String> actualRows = rowKeys(actual);
        if (expectedRows.equals(actualRows)) {
            return new ComparisonOutcome(true, true, true, false, List.of());
        }
        if (sameRowCount && sorted(expectedRows).equals(sorted(actualRows))) {
            return orderMatters
                    ? new ComparisonOutcome(false, true, true, true,
                    List.of("Les lignes sont correctes mais pas dans l'ordre demandé : vérifiez votre ORDER BY."))
                    : new ComparisonOutcome(true, true, true, false, List.of());
        }

        List<String> hints = new ArrayList<>();
        if (actual.truncated()) {
            hints.add("Votre requête renvoie beaucoup trop de lignes : vérifiez vos conditions de jointure et de filtre.");
        } else if (!sameRowCount) {
            hints.add("La solution attendue renvoie " + plural(expected.rowCount(), "ligne")
                    + ", votre requête en renvoie " + actual.rowCount() + ".");
        } else {
            hints.add("Le nombre de lignes est correct, mais certaines valeurs diffèrent.");
        }
        return new ComparisonOutcome(false, true, sameRowCount, false, hints);
    }

    private static List<String> rowKeys(QueryResult result) {
        return result.rows().stream()
                .map(row -> row.stream().map(ResultComparator::normalize).collect(Collectors.joining(SEPARATOR)))
                .toList();
    }

    private static String normalize(Object value) {
        if (value == null) {
            return NULL_MARKER;
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString())
                        .setScale(NUMERIC_SCALE, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString();
            } catch (NumberFormatException e) {
                return number.toString();
            }
        }
        return value.toString();
    }

    private static List<String> sorted(List<String> rows) {
        List<String> copy = new ArrayList<>(rows);
        Collections.sort(copy);
        return copy;
    }

    private static String plural(int count, String word) {
        return count + " " + word + (count > 1 ? "s" : "");
    }
}
