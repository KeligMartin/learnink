package com.learnink.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AdminDtos {

    private AdminDtos() {
    }

    /** @param datasource source de données de l'étudiant (1 si absente) */
    public record CreateStudentRequest(@NotBlank @Email String email, Integer datasource) {
    }

    public record ChangeDatasourceRequest(@NotNull Integer datasource) {
    }

    public record StudentDto(Long id, String email, boolean mustChangePassword, int datasource, int currentLevel,
                             int completedExercises, int totalExercises) {
    }

    /** Source de données proposée aux étudiants : un jeu de données et ses exercices. */
    public record DatasourceDto(int number, String name) {
    }

    /** Le mot de passe temporaire n'est renvoyé qu'une seule fois, à transmettre à l'étudiant. */
    public record StudentCredentialsDto(StudentDto student, String temporaryPassword) {
    }
}
