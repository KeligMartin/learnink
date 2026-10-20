package com.learnink.grading;

import com.learnink.query.QueryResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultComparatorTest {

    private final ResultComparator comparator = new ResultComparator();

    private static QueryResult result(List<List<Object>> rows) {
        return new QueryResult(List.of("a", "b"), rows, false, 0);
    }

    @Test
    void numbersAreComparedByValueAndColumnNamesAreIgnored() {
        QueryResult expected = result(List.of(List.of("x", 2L)));
        QueryResult actual = new QueryResult(List.of("autre", "nom"), List.of(List.of("x", new BigDecimal("2.00"))), false, 0);
        assertThat(comparator.compare(expected, actual, true).identical()).isTrue();
    }

    @Test
    void orderIsIgnoredUnlessRequired() {
        QueryResult expected = result(List.of(List.of("x", 1), List.of("y", 2)));
        QueryResult actual = result(List.of(List.of("y", 2), List.of("x", 1)));

        assertThat(comparator.compare(expected, actual, false).identical()).isTrue();
        ComparisonOutcome ordered = comparator.compare(expected, actual, true);
        assertThat(ordered.identical()).isFalse();
        assertThat(ordered.sameRowsInWrongOrder()).isTrue();
    }

    @Test
    void detectsWrongColumnAndRowCounts() {
        QueryResult expected = result(List.of(List.of("x", 1)));
        QueryResult threeColumns = new QueryResult(List.of("a", "b", "c"), List.of(List.of("x", 1, 3)), false, 0);
        QueryResult twoRows = result(List.of(List.of("x", 1), List.of("y", 2)));

        assertThat(comparator.compare(expected, threeColumns, false).sameColumnCount()).isFalse();
        ComparisonOutcome outcome = comparator.compare(expected, twoRows, false);
        assertThat(outcome.sameColumnCount()).isTrue();
        assertThat(outcome.sameRowCount()).isFalse();
        assertThat(outcome.hints()).singleElement().asString().contains("1 ligne");
    }
}
