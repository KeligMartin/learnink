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
            List<String> hints = new ArrayList<>();
            hints.add("La solution attendue renvoie " + plural(expectedColumns, "colonne")
                    + ", votre requête en renvoie " + actualColumns + ".");
            if (actual.columns().contains("_id") && !expected.columns().contains("_id")) {
                hints.add("MongoDB renvoie toujours le champ _id : excluez-le avec _id: 0 dans la projection.");
            }
            return new ComparisonOutcome(false, false, sameRowCount, false, hints);
        }

        List<String> expectedRows = rowKeys(expected);
        List<String> actualRows = rowKeys(actual);
        if (!expectedRows.equals(actualRows)) {
            // MongoDB ne garantit pas l'ordre des champs d'un document : les colonnes sont associées par contenu
            QueryResult aligned = alignColumns(expected, actual);
            if (aligned != null) {
                actualRows = rowKeys(aligned);
            }
        }
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

    /**
     * Réordonne les colonnes du résultat obtenu pour les faire correspondre à celles attendues, en associant les colonnes
     * qui contiennent les mêmes valeurs (à l'ordre des lignes près). Renvoie null si aucune association n'est possible
     * ou si les colonnes sont déjà dans le bon ordre.
     */
    static QueryResult alignColumns(QueryResult expected, QueryResult actual) {
        int columns = expected.columns().size();
        List<String> expectedSignatures = columnSignatures(expected);
        List<String> actualSignatures = columnSignatures(actual);
        int[] mapping = new int[columns];
        boolean[] used = new boolean[columns];
        boolean reordered = false;
        for (int i = 0; i < columns; i++) {
            mapping[i] = -1;
            // Préfère la même position, puis la première colonne libre de même contenu
            if (!used[i] && expectedSignatures.get(i).equals(actualSignatures.get(i))) {
                mapping[i] = i;
            } else {
                for (int j = 0; j < columns; j++) {
                    if (!used[j] && expectedSignatures.get(i).equals(actualSignatures.get(j))) {
                        mapping[i] = j;
                        break;
                    }
                }
            }
            if (mapping[i] < 0) {
                return null;
            }
            used[mapping[i]] = true;
            reordered |= mapping[i] != i;
        }
        if (!reordered) {
            return null;
        }
        List<List<Object>> rows = actual.rows().stream()
                .map(row -> {
                    List<Object> copy = new ArrayList<>(columns);
                    for (int i = 0; i < columns; i++) {
                        copy.add(row.get(mapping[i]));
                    }
                    return copy;
                })
                .toList();
        List<String> names = new ArrayList<>(columns);
        for (int i = 0; i < columns; i++) {
            names.add(actual.columns().get(mapping[i]));
        }
        return new QueryResult(names, rows, actual.truncated(), actual.executionTimeNanos());
    }

    private static List<String> columnSignatures(QueryResult result) {
        List<String> signatures = new ArrayList<>();
        for (int i = 0; i < result.columns().size(); i++) {
            int column = i;
            List<String> values = new ArrayList<>(result.rows().stream().map(row -> normalize(row.get(column))).toList());
            Collections.sort(values);
            signatures.add(String.join(SEPARATOR, values));
        }
        return signatures;
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
