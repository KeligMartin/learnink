package com.learnink.grading.quality.sql;

import com.learnink.query.sql.SqlText;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Requête de l'étudiant et solution de référence, sous forme d'arbre syntaxique et de texte normalisé
 * (minuscules, sans commentaires ni littéraux).
 */
public record SqlAnalysisContext(Statement student, String studentText, Statement reference, String referenceText) {

    public static SqlAnalysisContext of(Statement student, String studentSql, Statement reference, String referenceSql) {
        return new SqlAnalysisContext(student, SqlText.stripLiteralsAndComments(studentSql),
                reference, SqlText.stripLiteralsAndComments(referenceSql));
    }

    public Optional<PlainSelect> studentSelect() {
        return student instanceof PlainSelect select ? Optional.of(select) : Optional.empty();
    }

    public Optional<PlainSelect> referenceSelect() {
        return reference instanceof PlainSelect select ? Optional.of(select) : Optional.empty();
    }

    public boolean studentMatches(Pattern pattern) {
        return pattern.matcher(studentText).find();
    }

    public boolean referenceMatches(Pattern pattern) {
        return pattern.matcher(referenceText).find();
    }

    public static long count(Pattern pattern, String text) {
        return pattern.matcher(text).results().count();
    }
}
