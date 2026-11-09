package com.learnink.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Mot de passe par défaut d'un étudiant : la partie de son adresse avant « @ », suivie d'un suffixe
 * (plancelet@ecole.fr → plancelet2026). L'étudiant doit le changer à sa première connexion.
 */
@Component
public class DefaultPasswordGenerator {

    private final String suffix;

    public DefaultPasswordGenerator(@Value("${learnink.students.default-password-suffix:2026}") String suffix) {
        this.suffix = suffix.trim();
    }

    public String generate(String email) {
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        String localPart = at > 0 ? trimmed.substring(0, at) : trimmed;
        return localPart.toLowerCase(Locale.ROOT) + suffix;
    }
}
