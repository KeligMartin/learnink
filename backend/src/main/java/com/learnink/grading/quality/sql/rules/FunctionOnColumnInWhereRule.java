package com.learnink.grading.quality.sql.rules;

import com.learnink.grading.quality.QualityIssue;
import com.learnink.grading.quality.sql.SqlAnalysisContext;
import com.learnink.grading.quality.sql.SqlQualityRule;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Une fonction appliquée à une colonne dans le WHERE (ex. YEAR(date) = 2024) empêche l'utilisation des index.
 */
@Component
public class FunctionOnColumnInWhereRule implements SqlQualityRule {

    private static final Pattern FUNCTION_ON_COLUMN = Pattern.compile(
            "\\b(year|month|day|date|upper|lower|trim|substring|substr|left|right|concat|cast)\\s*\\(\\s*"
                    + "[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)?\\s*[,)]");

    @Override
    public Optional<QualityIssue> evaluate(SqlAnalysisContext context) {
        boolean student = context.studentSelect().map(FunctionOnColumnInWhereRule::hasFunctionInWhere).orElse(false);
        boolean reference = context.referenceSelect().map(FunctionOnColumnInWhereRule::hasFunctionInWhere).orElse(false);
        if (student && !reference) {
            return Optional.of(new QualityIssue(
                    "Une fonction appliquée à une colonne dans le WHERE empêche l'utilisation des index : "
                            + "comparez plutôt la colonne brute à une valeur (ex. une plage de dates).", 0.5));
        }
        return Optional.empty();
    }

    private static boolean hasFunctionInWhere(PlainSelect select) {
        return select.getWhere() != null
                && FUNCTION_ON_COLUMN.matcher(select.getWhere().toString().toLowerCase()).find();
    }
}
