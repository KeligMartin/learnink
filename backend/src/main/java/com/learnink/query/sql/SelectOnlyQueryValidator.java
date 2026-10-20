package com.learnink.query.sql;

import com.learnink.query.QueryRejectedException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garantit qu'une requête SQL est une unique instruction de lecture (SELECT).
 * Première ligne de défense : la connexion est également en lecture seule et l'utilisateur SQL n'a que le droit SELECT.
 */
@Component
public class SelectOnlyQueryValidator {

    static final int MAX_QUERY_LENGTH = 10_000;

    /** Fonctions ou clauses permettant d'accéder au système de fichiers, de bloquer ou de ralentir le serveur. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(sleep|benchmark|load_file|get_lock|release_lock|master_pos_wait|sys_exec|sys_eval"
                    + "|file_read|file_write|csvread|csvwrite|link_schema|java_object)\\s*\\("
                    + "|\\binto\\s+(outfile|dumpfile)\\b"
                    + "|\\bfor\\s+update\\b|\\block\\s+in\\s+share\\s+mode\\b");

    /**
     * @return la requête prête à être exécutée (sans point-virgule final)
     * @throws QueryRejectedException si la requête n'est pas une lecture autorisée
     */
    public String validate(String query) {
        if (query == null || query.isBlank()) {
            throw new QueryRejectedException("La requête est vide.");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new QueryRejectedException("La requête dépasse " + MAX_QUERY_LENGTH + " caractères.");
        }
        String sql = SqlText.withoutTrailingSemicolons(query);
        Statement statement = parseSingleStatement(sql);
        if (!(statement instanceof Select)) {
            throw new QueryRejectedException("Seules les requêtes de lecture (SELECT) sont autorisées.");
        }
        if (statement instanceof PlainSelect plainSelect && plainSelect.getIntoTables() != null) {
            throw new QueryRejectedException("SELECT ... INTO n'est pas autorisé.");
        }
        if (FORBIDDEN.matcher(SqlText.stripLiteralsAndComments(sql)).find()) {
            throw new QueryRejectedException("La requête utilise une fonction ou une clause interdite.");
        }
        return sql;
    }

    private Statement parseSingleStatement(String query) {
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(query);
        } catch (JSQLParserException e) {
            throw new QueryRejectedException("Erreur de syntaxe SQL : " + firstLine(e));
        }
        if (statements == null || statements.isEmpty()) {
            throw new QueryRejectedException("La requête est vide.");
        }
        if (statements.size() > 1) {
            throw new QueryRejectedException("Une seule requête à la fois est autorisée.");
        }
        return statements.get(0);
    }

    private static final Pattern UNEXPECTED_TOKEN =
            Pattern.compile("Encountered:\\s*(.*?), at line (\\d+), column (\\d+)");
    private static final Pattern TOKEN_IMAGE = Pattern.compile("/ \"(.*)\"");

    /** Transforme le message technique du parseur en message compréhensible par un étudiant. */
    private static String firstLine(JSQLParserException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage() == null ? "requête invalide" : cause.getMessage();
        Matcher matcher = UNEXPECTED_TOKEN.matcher(message);
        if (matcher.find()) {
            String position = " (ligne " + matcher.group(2) + ", colonne " + matcher.group(3) + ").";
            if (matcher.group(1).contains("<EOF>")) {
                return "la requête se termine de façon inattendue" + position;
            }
            Matcher token = TOKEN_IMAGE.matcher(matcher.group(1));
            return "élément inattendu « " + (token.find() ? token.group(1) : matcher.group(1)) + " »" + position;
        }
        return message.lines().findFirst().orElse(message);
    }
}
