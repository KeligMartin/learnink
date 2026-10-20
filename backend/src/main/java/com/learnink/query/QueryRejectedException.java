package com.learnink.query;

/** La requête a été refusée avant exécution (syntaxe invalide, écriture, fonction interdite...). */
public class QueryRejectedException extends RuntimeException {

    public QueryRejectedException(String message) {
        super(message);
    }
}
