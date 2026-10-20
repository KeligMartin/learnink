package com.learnink.auth;

import com.learnink.user.Role;
import com.learnink.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record UserDto(Long id, String email, Role role, boolean mustChangePassword, int datasource) {

        public static UserDto from(User user) {
            return new UserDto(user.getId(), user.getEmail(), user.getRole(), user.isMustChangePassword(),
                    user.getDatasource());
        }
    }

    public record LoginResponse(String token, UserDto user) {
    }
}
