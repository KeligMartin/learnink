package com.learnink.grading;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Traduit les erreurs fréquentes du moteur en conseils pédagogiques,
 * en particulier sur le regroupement (GROUP BY / HAVING).
 * Reconnaît les messages de H2 (français et anglais) et de MySQL.
 */
@Component
public class ExecutionErrorAdvisor {

    public List<String> adviceFor(String errorMessage) {
        List<String> hints = new ArrayList<>();
        if (errorMessage == null) {
            return hints;
        }
        String message = errorMessage.toLowerCase(Locale.ROOT);
        if (message.contains("group by") || message.contains("group columns")) {
            hints.add("Chaque colonne du SELECT qui n'est pas dans une fonction d'agrégat (COUNT, SUM, AVG, MIN, MAX) "
                    + "doit figurer dans le GROUP BY.");
        }
        if (message.contains("fonction agrégée") || message.contains("aggregate function")
                || message.contains("group function")) {
            hints.add("Une condition sur un agrégat (ex. COUNT(*) > 3) se place dans HAVING, après le GROUP BY, "
                    + "et non dans WHERE.");
        }
        return hints;
    }
}
