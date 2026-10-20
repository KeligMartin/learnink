package com.learnink.grading.quality;

import com.learnink.grading.quality.sql.SqlQualityAnalyzer;
import com.learnink.grading.quality.sql.rules.NonAggregateHavingRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NonAggregateHavingRuleTest {

    private static final String REFERENCE =
            "SELECT batiment_id, COUNT(*) FROM chambre WHERE capacite >= 2 GROUP BY batiment_id HAVING COUNT(*) > 5";

    private final SqlQualityAnalyzer analyzer = new SqlQualityAnalyzer(List.of(new NonAggregateHavingRule()));

    @Test
    void acceptsAggregateConditionsInHaving() {
        assertThat(analyzer.analyze(REFERENCE, REFERENCE)).isEmpty();
    }

    @Test
    void flagsRowConditionsPlacedInHaving() {
        String student = "SELECT batiment_id, COUNT(*) FROM chambre GROUP BY batiment_id, capacite "
                + "HAVING COUNT(*) > 5 AND capacite >= 2";
        assertThat(analyzer.analyze(student, REFERENCE))
                .singleElement()
                .extracting(QualityIssue::message).asString()
                .contains("WHERE");
    }
}
