package com.learnink.query.sql;

/** Chargement de la base bac à sable au démarrage de l'application. */
public enum SandboxInitMode {
    /** Jamais : la base est gérée en dehors de l'application. */
    NEVER,
    /** Seulement si les scripts ont changé depuis le dernier chargement (production : plusieurs instances, redémarrages fréquents). */
    IF_CHANGED,
    /** À chaque démarrage (développement, base en mémoire). */
    ALWAYS
}
