package com.learnink.seed;

import com.learnink.user.Role;
import com.learnink.user.User;
import com.learnink.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.learnink.startup.StartupOrder;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comptes de démonstration, activés uniquement en développement (learnink.seed.demo-users=true).
 */
@Component
@Order(StartupOrder.DEMO_USERS)
@ConditionalOnProperty(name = "learnink.seed.demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    static final String TEACHER_EMAIL = "prof@learnink.dev";
    static final String TEACHER_PASSWORD = "Prof1234";
    static final String STUDENT_EMAIL = "etudiant@learnink.dev";
    static final String STUDENT_PASSWORD = "Etudiant1";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmailIgnoreCase(TEACHER_EMAIL)) {
            User teacher = new User(TEACHER_EMAIL, passwordEncoder.encode(TEACHER_PASSWORD), Role.TEACHER);
            teacher.changePassword(passwordEncoder.encode(TEACHER_PASSWORD));
            userRepository.save(teacher);
            log.info("Compte enseignant de démonstration créé : {}", TEACHER_EMAIL);
        }
        if (!userRepository.existsByEmailIgnoreCase(STUDENT_EMAIL)) {
            // Mot de passe temporaire : l'étudiant devra le changer à la première connexion
            userRepository.save(new User(STUDENT_EMAIL, passwordEncoder.encode(STUDENT_PASSWORD), Role.STUDENT));
            log.info("Compte étudiant de démonstration créé : {}", STUDENT_EMAIL);
        }
    }
}
