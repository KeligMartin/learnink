package com.learnink.query;

/** Le moteur a renvoyé une erreur pendant l'exécution (table inconnue, délai dépassé...). */
public class QueryExecutionException extends RuntimeException {

    public QueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
