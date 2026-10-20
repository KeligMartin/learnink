package com.learnink.grading.quality.sql;

import com.learnink.grading.quality.QualityIssue;
import com.learnink.grading.quality.QueryQualityAnalyzer;
import com.learnink.query.QueryEngine;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SqlQualityAnalyzer implements QueryQualityAnalyzer {

    private final List<SqlQualityRule> rules;

    public SqlQualityAnalyzer(List<SqlQualityRule> rules) {
        this.rules = rules;
    }

    @Override
    public QueryEngine engine() {
        return QueryEngine.SQL;
    }

    @Override
    public List<QualityIssue> analyze(String studentQuery, String referenceQuery) {
        try {
            Statement student = CCJSqlParserUtil.parse(studentQuery);
            Statement reference = CCJSqlParserUtil.parse(referenceQuery);
            SqlAnalysisContext context = SqlAnalysisContext.of(student, studentQuery, reference, referenceQuery);
            return rules.stream()
                    .map(rule -> rule.evaluate(context))
                    .flatMap(Optional::stream)
                    .toList();
        } catch (JSQLParserException e) {
            return List.of();
        }
    }
}
