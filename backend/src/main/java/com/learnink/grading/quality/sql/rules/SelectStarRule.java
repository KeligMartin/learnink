package com.learnink.grading.quality.sql.rules;

import com.learnink.grading.quality.QualityIssue;
import com.learnink.grading.quality.sql.SqlAnalysisContext;
import com.learnink.grading.quality.sql.SqlQualityRule;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class SelectStarRule implements SqlQualityRule {

    private static final Pattern SELECT_STAR =
            Pattern.compile("(\\bselect|,)\\s*(distinct\\s+)?([a-z_][a-z0-9_]*\\.)?\\*");

    @Override
    public Optional<QualityIssue> evaluate(SqlAnalysisContext context) {
        if (context.studentMatches(SELECT_STAR) && !context.referenceMatches(SELECT_STAR)) {
            return Optional.of(new QualityIssue(
                    "Évitez SELECT * : ne sélectionnez que les colonnes nécessaires.", 1));
        }
        return Optional.empty();
    }
}
