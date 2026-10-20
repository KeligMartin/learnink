package com.learnink.query;

/**
 * Stratégie d'exécution d'une requête étudiante sur un moteur de base de données donné.
 * Chaque implémentation est responsable de garantir que la requête est en lecture seule.
 */
public interface QueryExecutionStrategy {

    QueryEngine engine();

    /** Indique si le moteur est configuré et utilisable. */
    boolean isAvailable();

    /**
     * Valide puis exécute la requête sur la source de données de la cible.
     *
     * @throws QueryRejectedException  si la requête n'est pas autorisée (écriture, syntaxe invalide...)
     * @throws QueryExecutionException si le moteur renvoie une erreur ou dépasse le délai autorisé
     */
    QueryResult execute(String query, QueryTarget target);

    /** Décrit les collections / tables interrogeables par les étudiants. */
    SchemaDescription describeSchema(QueryTarget target);
}
