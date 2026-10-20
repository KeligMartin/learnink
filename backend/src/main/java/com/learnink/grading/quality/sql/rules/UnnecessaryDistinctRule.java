package com.learnink.grading.quality.sql.rules;

import com.learnink.grading.quality.QualityIssue;
import com.learnink.grading.quality.sql.SqlAnalysisContext;
import com.learnink.grading.quality.sql.SqlQualityRule;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class UnnecessaryDistinctRule implements SqlQualityRule {

    private static final Pattern SELECT_DISTINCT = Pattern.compile("\\bselect\\s+distinct\\b");

    @Override
    public Optional<QualityIssue> evaluate(SqlAnalysisContext context) {
        if (context.studentMatches(SELECT_DISTINCT) && !context.referenceMatches(SELECT_DISTINCT)) {
            return Optional.of(new QualityIssue(
                    "DISTINCT semble inutile ici : il impose un dédoublonnage coûteux.", 0.5));
        }
        return Optional.empty();
    }
}
