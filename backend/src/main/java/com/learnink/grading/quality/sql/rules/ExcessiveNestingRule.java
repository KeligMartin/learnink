package com.learnink.grading.quality.sql.rules;

import com.learnink.grading.quality.QualityIssue;
import com.learnink.grading.quality.sql.SqlAnalysisContext;
import com.learnink.grading.quality.sql.SqlQualityRule;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/** Pénalise une requête qui contient nettement plus de sous-requêtes que la solution de référence. */
@Component
public class ExcessiveNestingRule implements SqlQualityRule {

    private static final Pattern SELECT = Pattern.compile("\\bselect\\b");
    private static final int TOLERANCE = 1;

    @Override
    public Optional<QualityIssue> evaluate(SqlAnalysisContext context) {
        long student = SqlAnalysisContext.count(SELECT, context.studentText());
        long reference = SqlAnalysisContext.count(SELECT, context.referenceText());
        if (student > reference + TOLERANCE) {
            return Optional.of(new QualityIssue(
                    "Requête plus imbriquée que nécessaire (" + student + " SELECT contre " + reference
                            + " dans la solution) : une jointure ou un regroupement serait plus simple.", 0.5));
        }
        return Optional.empty();
    }
}
