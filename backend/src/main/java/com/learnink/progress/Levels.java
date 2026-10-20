package com.learnink.progress;

/** Libellés des 3 niveaux de difficulté. */
public final class Levels {

    private Levels() {
    }

    public static String label(int level) {
        return switch (level) {
            case 1 -> "Débutant";
            case 2 -> "Intermédiaire";
            case 3 -> "Avancé";
            default -> "Niveau " + level;
        };
    }
}
