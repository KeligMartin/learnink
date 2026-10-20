package com.learnink.seed;

import com.learnink.auth.PasswordPolicy;
import com.learnink.user.Role;
import com.learnink.user.User;
import com.learnink.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import com.learnink.startup.StartupOrder;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le premier compte enseignant en production, à partir de LEARNINK_TEACHER_EMAIL et LEARNINK_TEACHER_PASSWORD.
 * Sans effet si les variables sont vides ou si le compte existe déjà. Le mot de passe devra être changé à la première
 * connexion : il peut donc être retiré de la configuration de l'hébergeur ensuite.
 */
@Component
@Order(StartupOrder.TEACHER)
public class TeacherBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TeacherBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final String email;
    private final String password;

    public TeacherBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                            @Value("${learnink.bootstrap.teacher-email:}") String email,
                            @Value("${learnink.bootstrap.teacher-password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.email = email.trim();
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty() || userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        passwordPolicy.validate(password);
        userRepository.save(new User(email, passwordEncoder.encode(password), Role.TEACHER));
        log.info("Compte enseignant initial créé : {}", email);
    }
}
