package com.learnink.query.sql;

import java.util.Locale;
import java.util.Set;

/**
 * Tables et schémas que les requêtes étudiantes ne doivent jamais lire, même si la base applicative et une base
 * bac à sable partagent la même base MySQL (cas des hébergeurs qui ne fournissent qu'une base par offre).
 */
public final class ProtectedTables {

    /** Tables de l'application : comptes (empreintes de mots de passe), solutions des exercices, tentatives... */
    private static final Set<String> APPLICATION_TABLES =
            Set.of("app_user", "exercise", "submission", "sandbox_state", "flyway_schema_history");

    /** Schémas système exposant la structure ou la configuration du serveur. */
    private static final Set<String> SYSTEM_SCHEMAS =
            Set.of("information_schema", "mysql", "performance_schema", "sys");

    private ProtectedTables() {
    }

    /** @param qualifiedName nom de table tel qu'écrit dans la requête, éventuellement préfixé (schema.table) */
    public static boolean isProtected(String qualifiedName) {
        String[] parts = qualifiedName.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT).split("\\.");
        String table = parts[parts.length - 1];
        boolean systemSchema = parts.length > 1 && SYSTEM_SCHEMAS.contains(parts[parts.length - 2]);
        return systemSchema || APPLICATION_TABLES.contains(table);
    }
}
