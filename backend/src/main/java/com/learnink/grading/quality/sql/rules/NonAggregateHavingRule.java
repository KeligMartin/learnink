package com.learnink.grading.quality.sql.rules;

import com.learnink.grading.quality.QualityIssue;
import com.learnink.grading.quality.sql.SqlAnalysisContext;
import com.learnink.grading.quality.sql.SqlQualityRule;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Une condition du HAVING qui ne porte sur aucun agrégat (ex. HAVING ville = 'Rennes') devrait être dans le WHERE :
 * elle filtrerait les lignes avant le regroupement au lieu de filtrer les groupes après coup.
 */
@Component
public class NonAggregateHavingRule implements SqlQualityRule {

    private static final Pattern AGGREGATE = Pattern.compile("\\b(count|sum|avg|min|max)\\s*\\(");

    @Override
    public Optional<QualityIssue> evaluate(SqlAnalysisContext context) {
        boolean student = context.studentSelect().map(NonAggregateHavingRule::hasNonAggregateCondition).orElse(false);
        boolean reference = context.referenceSelect().map(NonAggregateHavingRule::hasNonAggregateCondition).orElse(false);
        if (student && !reference) {
            return Optional.of(new QualityIssue(
                    "Une condition du HAVING ne contient aucun agrégat (COUNT, SUM...) : placez-la dans le WHERE "
                            + "pour filtrer les lignes avant le regroupement.", 0.5));
        }
        return Optional.empty();
    }

    private static boolean hasNonAggregateCondition(PlainSelect select) {
        return select.getHaving() != null && containsNonAggregateCondition(select.getHaving());
    }

    private static boolean containsNonAggregateCondition(Expression expression) {
        if (expression instanceof AndExpression and) {
            return containsNonAggregateCondition(and.getLeftExpression())
                    || containsNonAggregateCondition(and.getRightExpression());
        }
        return !AGGREGATE.matcher(expression.toString().toLowerCase()).find();
    }
}
