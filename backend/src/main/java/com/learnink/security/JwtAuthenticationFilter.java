package com.learnink.security;

import com.learnink.user.User;
import com.learnink.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authentifie la requête à partir du jeton "Bearer".
 * L'utilisateur est rechargé à chaque requête : un changement de mot de passe ou de rôle est pris en compte immédiatement.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService.extractUserId(header.substring(BEARER_PREFIX.length()))
                    .flatMap(userRepository::findById)
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(User user) {
        String authority = user.isMustChangePassword()
                ? Authorities.PASSWORD_CHANGE_REQUIRED
                : "ROLE_" + user.getRole().name();
        var authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.from(user), null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
