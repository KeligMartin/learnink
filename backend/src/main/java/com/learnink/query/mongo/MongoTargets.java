package com.learnink.query.mongo;

import com.learnink.query.EngineUnavailableException;
import com.learnink.query.QueryTarget;
import com.learnink.user.User;
import com.learnink.user.UserRepository;
import com.mongodb.ConnectionString;
import org.springframework.stereotype.Component;

/**
 * Détermine la base MongoDB interrogée : la base personnelle de l'étudiant s'il l'a renseignée,
 * sinon la base partagée de sa source de données (si la plateforme en a une).
 */
@Component
public class MongoTargets {

    /**
     * @param personal vrai pour la base d'un étudiant, faux pour la base partagée de la plateforme
     */
    public record Location(String uri, String database, boolean personal) {
    }

    private final UserRepository userRepository;
    private final MongoProperties properties;

    public MongoTargets(UserRepository userRepository, MongoProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
    }

    public Location resolve(QueryTarget target) {
        String personalUri = personalUri(target);
        if (personalUri != null) {
            return new Location(personalUri, databaseOf(personalUri, properties.database()), true);
        }
        MongoProperties.Dataset dataset = properties.datasets().get(target.datasource());
        if (dataset != null && dataset.hasSharedDatabase()) {
            return new Location(dataset.uri(), sharedDatabase(dataset), false);
        }
        throw new EngineUnavailableException(target.userId() == null
                ? "Aucune base MongoDB partagée n'est configurée pour la source de données n°" + target.datasource() + "."
                : "Renseignez d'abord votre base MongoDB dans « Ma base MongoDB » (menu du haut), "
                + "après y avoir importé le jeu de données.");
    }

    /** Vrai si l'utilisateur interroge sa propre base (la solution de référence s'exécute alors sur la même base). */
    public boolean usesPersonalDatabase(QueryTarget target) {
        return personalUri(target) != null;
    }

    private String personalUri(QueryTarget target) {
        if (target.userId() == null) {
            return null;
        }
        return userRepository.findById(target.userId()).map(User::getMongoUri).orElse(null);
    }

    public static String sharedDatabase(MongoProperties.Dataset dataset) {
        if (dataset.database() != null && !dataset.database().isBlank()) {
            return dataset.database();
        }
        return databaseOf(dataset.uri(), "learnink_" + dataset.name());
    }

    static String databaseOf(String uri, String fallback) {
        String database = new ConnectionString(uri).getDatabase();
        return database == null || database.isBlank() ? fallback : database;
    }
}
