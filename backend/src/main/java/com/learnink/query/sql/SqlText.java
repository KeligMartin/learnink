package com.learnink.query.sql;

/**
 * Outils de manipulation textuelle d'une requête SQL.
 */
public final class SqlText {

    private SqlText() {
    }

    /**
     * Retire les commentaires et remplace le contenu des chaînes littérales par une chaîne vide,
     * afin de pouvoir rechercher des mots-clés sans faux positifs (ex. {@code WHERE titre = 'sleep('}).
     * Le résultat est en minuscules.
     */
    public static String stripLiteralsAndComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (c == '\'' || c == '"') {
                i = skipQuoted(sql, i, c);
                out.append(c).append(c);
            } else if (c == '-' && next == '-' || c == '#') {
                i = skipUntilEndOfLine(sql, i);
                out.append(' ');
            } else if (c == '/' && next == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? sql.length() : end + 2;
                out.append(' ');
            } else {
                out.append(Character.toLowerCase(c));
                i++;
            }
        }
        return out.toString();
    }

    /** Supprime les points-virgules et espaces en fin de requête. */
    public static String withoutTrailingSemicolons(String sql) {
        String result = sql.strip();
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).strip();
        }
        return result;
    }

    private static int skipQuoted(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        return sql.length();
    }

    private static int skipUntilEndOfLine(String sql, int start) {
        int end = sql.indexOf('\n', start);
        return end < 0 ? sql.length() : end;
    }
}
