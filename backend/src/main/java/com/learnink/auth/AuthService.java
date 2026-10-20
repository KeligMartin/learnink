package com.learnink.auth;

import com.learnink.auth.AuthDtos.LoginResponse;
import com.learnink.auth.AuthDtos.UserDto;
import com.learnink.common.InvalidRequestException;
import com.learnink.common.ResourceNotFoundException;
import com.learnink.security.JwtService;
import com.learnink.user.User;
import com.learnink.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Email ou mot de passe incorrect."));
        return new LoginResponse(jwtService.generateToken(user), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public UserDto currentUser(Long userId) {
        return UserDto.from(findUser(userId));
    }

    @Transactional
    public UserDto changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidRequestException("Le mot de passe actuel est incorrect.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new InvalidRequestException("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        passwordPolicy.validate(newPassword);
        user.changePassword(passwordEncoder.encode(newPassword));
        return UserDto.from(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable."));
    }
}
