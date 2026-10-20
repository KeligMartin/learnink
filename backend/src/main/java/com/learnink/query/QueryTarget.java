package com.learnink.query;

import com.learnink.security.AuthenticatedUser;

/**
 * Où exécuter une requête : la source de données affectée à l'utilisateur (1, 2...).
 * L'identifiant de l'utilisateur permettra aux moteurs "une base par étudiant" (Firestore) de cibler la sienne.
 */
public record QueryTarget(int datasource, Long userId) {

    public static QueryTarget of(AuthenticatedUser user) {
        return new QueryTarget(user.datasource(), user.id());
    }

    /** Cible technique, hors requête d'un utilisateur (vérification des solutions au démarrage). */
    public static QueryTarget system(int datasource) {
        return new QueryTarget(datasource, null);
    }
}
