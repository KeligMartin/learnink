package com.learnink.progress;

public class LevelLockedException extends RuntimeException {

    public LevelLockedException(int level) {
        super("Le niveau " + level + " est verrouillé : terminez d'abord le niveau précédent.");
    }
}
