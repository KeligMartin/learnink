package com.learnink.admin;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class TemporaryPasswordGenerator {

    /** Caractères sans ambiguïté visuelle (pas de 0/O, 1/l/I). */
    private static final String LETTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH - 2; i++) {
            password.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        for (int i = 0; i < 2; i++) {
            password.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return password.toString();
    }
}
