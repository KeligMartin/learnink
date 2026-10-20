package com.learnink.startup;

import org.springframework.core.Ordered;

/**
 * Ordre des tâches de démarrage. Elles s'exécutent sous un verrou partagé par toutes les instances,
 * car l'hébergeur peut démarrer plusieurs instances en même temps.
 */
public final class StartupOrder {

    public static final int ACQUIRE_LOCK = Ordered.HIGHEST_PRECEDENCE;
    public static final int SANDBOX = 10;
    public static final int EXERCISES = 20;
    public static final int DEMO_USERS = 30;
    public static final int TEACHER = 40;
    public static final int RELEASE_LOCK = Ordered.LOWEST_PRECEDENCE;

    private StartupOrder() {
    }
}
