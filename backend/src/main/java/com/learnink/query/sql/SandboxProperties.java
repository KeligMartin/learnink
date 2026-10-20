package com.learnink.query.sql;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Configuration des bases "bac à sable" interrogées par les étudiants.
 *
 * @param queryTimeoutSeconds durée maximale d'exécution d'une requête
 * @param maxComparedRows     nombre maximal de lignes lues (et comparées à la solution)
 * @param maxDisplayedRows    nombre maximal de lignes renvoyées à l'interface
 * @param initMode            chargement du schéma et des données au démarrage
 * @param datasources         sources de données, indexées par numéro (champ "datasource" des utilisateurs et des
 *                            exercices) : chacune est un jeu de données distinct avec ses propres exercices
 */
@ConfigurationProperties("learnink.sandbox")
public record SandboxProperties(int queryTimeoutSeconds, int maxComparedRows, int maxDisplayedRows,
                                SandboxInitMode initMode, Map<Integer, Datasource> datasources) {

    /**
     * @param name                         libellé du jeu de données (ex. « Résidence universitaire »)
     * @param username / password          utilisateur des requêtes étudiantes : SELECT uniquement
     * @param adminUrl                     URL utilisée pour le chargement (par défaut : url)
     * @param adminUsername / adminPassword utilisateur disposant des droits d'écriture, pour le chargement des données
     * @param scripts                      scripts de chargement (schéma + données) propres à ce jeu de données
     * @param connectionInitSql            requête exécutée à l'ouverture de chaque connexion (ex. mode SQL)
     */
    public record Datasource(String name, String url, String username, String password, int maximumPoolSize,
                             String connectionInitSql, String adminUrl, String adminUsername, String adminPassword,
                             List<String> scripts) {

        public String loadUrl() {
            return adminUrl == null || adminUrl.isBlank() ? url : adminUrl;
        }

        public List<String> scriptsOrEmpty() {
            return scripts == null ? List.of() : scripts;
        }
    }

    public SandboxInitMode effectiveInitMode() {
        return initMode == null ? SandboxInitMode.NEVER : initMode;
    }

    /** Bases réellement configurées : une entrée sans URL (ex. variable d'environnement absente) est ignorée. */
    public Map<Integer, Datasource> configuredDatasources() {
        Map<Integer, Datasource> configured = new TreeMap<>();
        if (datasources != null) {
            datasources.forEach((number, datasource) -> {
                if (datasource != null && datasource.url() != null && !datasource.url().isBlank()) {
                    configured.put(number, datasource);
                }
            });
        }
        return configured;
    }
}
