package com.learnink.auth;

import com.learnink.common.InvalidRequestException;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 8;

    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new InvalidRequestException("Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères.");
        }
        if (password.chars().noneMatch(Character::isDigit) || password.chars().noneMatch(Character::isLetter)) {
            throw new InvalidRequestException("Le mot de passe doit contenir au moins une lettre et un chiffre.");
        }
    }
}
