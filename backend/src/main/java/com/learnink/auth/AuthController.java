package com.learnink.auth;

import com.learnink.auth.AuthDtos.ChangePasswordRequest;
import com.learnink.auth.AuthDtos.LoginRequest;
import com.learnink.auth.AuthDtos.LoginResponse;
import com.learnink.auth.AuthDtos.UserDto;
import com.learnink.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.currentUser(user.id());
    }

    @PostMapping("/change-password")
    public UserDto changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(user.id(), request.currentPassword(), request.newPassword());
    }
}
