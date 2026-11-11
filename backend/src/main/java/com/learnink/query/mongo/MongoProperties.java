package com.learnink.query.mongo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Configuration du moteur MongoDB.
 *
 * @param database       base utilisée quand la chaîne de connexion d'un étudiant n'en précise pas
 * @param allowedHosts   domaines autorisés pour les bases personnelles des étudiants (ex. mongodb.net) : la plateforme
 *                       ne se connecte à aucun autre serveur
 * @param timeoutSeconds délai de connexion et d'exécution d'une requête
 * @param maxClients     nombre maximal de connexions (bases d'étudiants) gardées ouvertes
 * @param sharedPoolSize connexions ouvertes au plus vers chaque base partagée (limite de l'offre de l'hébergeur)
 * @param datasets       jeu de données de chaque source (1 : résidence, 2 : transport), avec une base partagée facultative
 */
@ConfigurationProperties("learnink.mongo")
public record MongoProperties(String database, List<String> allowedHosts, int timeoutSeconds, int maxClients,
                              int sharedPoolSize, Map<Integer, Dataset> datasets) {

    public MongoProperties {
        database = database == null || database.isBlank() ? "learnink" : database;
        allowedHosts = allowedHosts == null ? List.of() : allowedHosts.stream()
                .map(String::strip).filter(host -> !host.isEmpty()).toList();
        timeoutSeconds = timeoutSeconds <= 0 ? 5 : timeoutSeconds;
        maxClients = maxClients <= 0 ? 60 : maxClients;
        sharedPoolSize = sharedPoolSize <= 0 ? 10 : sharedPoolSize;
        datasets = datasets == null ? Map.of() : datasets;
    }

    /**
     * @param name     dossier des fichiers JSON (classpath:mongo/{name}/*.json)
     * @param uri      base partagée de la plateforme (facultative) : utilisée par les étudiants sans base personnelle
     *                 et chargée automatiquement au démarrage
     * @param database nom de la base partagée (par défaut celui de l'URI, sinon learnink_{name})
     */
    public record Dataset(String name, String uri, String database) {

        public boolean hasSharedDatabase() {
            return uri != null && !uri.isBlank();
        }
    }
}
