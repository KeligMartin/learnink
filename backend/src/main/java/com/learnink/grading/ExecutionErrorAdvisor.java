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
        addMongoAdvice(message, hints);
        return hints;
    }

    /** Messages d'erreur fréquents de MongoDB. */
    private static void addMongoAdvice(String message, List<String> hints) {
        if (message.contains("unknown operator") || message.contains("unrecognized expression")
                || message.contains("unknown top level operator")) {
            hints.add("Opérateur inconnu : vérifiez son orthographe ($gte, $lt, $in, $regex…) et qu'il commence par $.");
        }
        if (message.contains("unrecognized pipeline stage")) {
            hints.add("Étape de pipeline inconnue : les étapes courantes sont $match, $project, $group, $sort, $limit, "
                    + "$unwind et $lookup.");
        }
        if (message.contains("group aggregate field") || message.contains("the group aggregate field")
                || message.contains("must be an accumulator")) {
            hints.add("Dans $group, chaque champ autre que _id doit utiliser un accumulateur : { total: { $sum: 1 } }.");
        }
        if (message.contains("a group specification must include an _id")) {
            hints.add("$group exige un _id : la clé de regroupement, ex. { _id: \"$type\" } (ou null pour tout regrouper).");
        }
        if (message.contains("cannot do inclusion on field") || message.contains("cannot do exclusion on field")) {
            hints.add("Une projection ne peut pas mélanger inclusion (1) et exclusion (0), sauf pour _id.");
        }
        if (message.contains("fieldpath field names may not contain '.'") || message.contains("fieldpath")) {
            hints.add("Dans une expression, un champ s'écrit avec $ et entre guillemets : \"$prix_indicatif\", "
                    + "\"$batiment.nom\".");
        }
    }
}
