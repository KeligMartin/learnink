package com.learnink.grading.quality.sql.rules;

import com.learnink.grading.quality.QualityIssue;
import com.learnink.grading.quality.sql.SqlAnalysisContext;
import com.learnink.grading.quality.sql.SqlQualityRule;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Détecte les jointures "à l'ancienne" : FROM a, b WHERE a.id = b.a_id. */
@Component
public class ImplicitJoinRule implements SqlQualityRule {

    @Override
    public Optional<QualityIssue> evaluate(SqlAnalysisContext context) {
        boolean student = context.studentSelect().map(ImplicitJoinRule::hasImplicitJoin).orElse(false);
        boolean reference = context.referenceSelect().map(ImplicitJoinRule::hasImplicitJoin).orElse(false);
        if (student && !reference) {
            return Optional.of(new QualityIssue(
                    "Préférez une jointure explicite (JOIN ... ON) à une liste de tables séparées par des virgules.", 0.5));
        }
        return Optional.empty();
    }

    private static boolean hasImplicitJoin(PlainSelect select) {
        return select.getJoins() != null && select.getJoins().stream().anyMatch(Join::isSimple);
    }
}
