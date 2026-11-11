package com.learnink.query.mongo;

import com.learnink.common.InvalidRequestException;
import com.mongodb.ConnectionString;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Vérifie la chaîne de connexion saisie par un étudiant avant toute connexion : format MongoDB, et serveur appartenant
 * à un domaine autorisé (la plateforme ne doit pas servir à joindre d'autres machines).
 */
@Component
public class MongoUriValidator {

    private final MongoProperties properties;

    public MongoUriValidator(MongoProperties properties) {
        this.properties = properties;
    }

    public ConnectionString validate(String uri) {
        String value = uri == null ? "" : uri.strip();
        if (!value.startsWith("mongodb+srv://") && !value.startsWith("mongodb://")) {
            throw new InvalidRequestException("La chaîne de connexion doit commencer par mongodb+srv:// ou mongodb://.");
        }
        if (value.contains("<password>") || value.contains("<db_password>")) {
            throw new InvalidRequestException("Remplacez <db_password> par le mot de passe de l'utilisateur de la base.");
        }
        ConnectionString connection;
        try {
            connection = new ConnectionString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Chaîne de connexion invalide : " + e.getMessage());
        }
        if (connection.getUsername() == null || connection.getPassword() == null) {
            throw new InvalidRequestException("La chaîne de connexion doit contenir l'utilisateur et le mot de passe : "
                    + "mongodb+srv://utilisateur:motdepasse@cluster…");
        }
        for (String host : connection.getHosts()) {
            String name = hostName(host);
            if (!allowed(name)) {
                throw new InvalidRequestException("Le serveur « " + name + " » n'est pas autorisé. Domaines acceptés : "
                        + String.join(", ", properties.allowedHosts()) + ".");
            }
        }
        return connection;
    }

    private boolean allowed(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return properties.allowedHosts().stream()
                .map(domain -> domain.toLowerCase(Locale.ROOT).replaceFirst("^\\*?\\.", ""))
                .anyMatch(domain -> lower.equals(domain) || lower.endsWith("." + domain));
    }

    private static String hostName(String host) {
        if (host.startsWith("[")) {
            return host.substring(1, host.indexOf(']'));
        }
        int colon = host.lastIndexOf(':');
        return colon > 0 ? host.substring(0, colon) : host;
    }

    /** Chaîne affichable : le mot de passe est masqué. */
    public static String mask(String uri) {
        if (uri == null) {
            return null;
        }
        return uri.replaceFirst("^(mongodb(?:\\+srv)?://[^:/@]+:)[^@]*@", "$1••••••@");
    }
}
