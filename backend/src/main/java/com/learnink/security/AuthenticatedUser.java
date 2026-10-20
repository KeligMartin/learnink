package com.learnink.security;

import com.learnink.user.Role;
import com.learnink.user.User;

/**
 * Utilisateur connecté, placé comme "principal" dans le contexte de sécurité.
 */
public record AuthenticatedUser(Long id, String email, Role role, int datasource) {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole(), user.getDatasource());
    }

    public boolean isTeacher() {
        return role == Role.TEACHER;
    }
}
