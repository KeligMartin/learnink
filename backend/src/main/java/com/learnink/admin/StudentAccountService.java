package com.learnink.admin;

import com.learnink.admin.AdminDtos.StudentCredentialsDto;
import com.learnink.admin.AdminDtos.StudentDto;
import com.learnink.auth.AuthDtos.UserDto;
import com.learnink.common.InvalidRequestException;
import com.learnink.common.ResourceNotFoundException;
import com.learnink.progress.ProgressDtos.ProgressDto;
import com.learnink.progress.ProgressService;
import com.learnink.query.QueryEngine;
import com.learnink.user.Role;
import com.learnink.user.User;
import com.learnink.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final ProgressService progressService;
    private final DatasourceCatalog datasourceCatalog;

    public StudentAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                 TemporaryPasswordGenerator passwordGenerator, ProgressService progressService,
                                 DatasourceCatalog datasourceCatalog) {
        this.datasourceCatalog = datasourceCatalog;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.progressService = progressService;
    }

    public List<StudentDto> listStudents(QueryEngine engine) {
        return userRepository.findAllByRoleOrderByEmail(Role.STUDENT).stream()
                .map(student -> toDto(student, engine))
                .toList();
    }

    @Transactional
    public StudentCredentialsDto createStudent(String email, Integer datasource) {
        int number = datasource == null ? 1 : datasource;
        datasourceCatalog.assertExists(number);
        if (userRepository.existsByEmailIgnoreCase(email.trim())) {
            throw new InvalidRequestException("Un compte existe déjà pour " + email + ".");
        }
        String temporaryPassword = passwordGenerator.generate();
        User student = new User(email.trim(), passwordEncoder.encode(temporaryPassword), Role.STUDENT);
        student.assignDatasource(number);
        userRepository.save(student);
        return new StudentCredentialsDto(toDto(student, QueryEngine.SQL), temporaryPassword);
    }

    @Transactional
    public StudentCredentialsDto resetPassword(Long studentId) {
        User student = findStudent(studentId);
        String temporaryPassword = passwordGenerator.generate();
        student.resetPassword(passwordEncoder.encode(temporaryPassword));
        return new StudentCredentialsDto(toDto(student, QueryEngine.SQL), temporaryPassword);
    }

    @Transactional
    public StudentDto changeDatasource(Long studentId, int datasource) {
        datasourceCatalog.assertExists(datasource);
        User student = findStudent(studentId);
        student.assignDatasource(datasource);
        return toDto(student, QueryEngine.SQL);
    }

    /** L'enseignant change sa propre source de données pour consulter les exercices d'un autre jeu. */
    @Transactional
    public UserDto changeOwnDatasource(Long teacherId, int datasource) {
        datasourceCatalog.assertExists(datasource);
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable."));
        teacher.assignDatasource(datasource);
        return UserDto.from(teacher);
    }

    private User findStudent(Long studentId) {
        return userRepository.findById(studentId)
                .filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> new ResourceNotFoundException("Étudiant introuvable."));
    }

    private StudentDto toDto(User student, QueryEngine engine) {
        ProgressDto progress = progressService.progress(student.getId(), false, engine, student.getDatasource());
        return new StudentDto(student.getId(), student.getEmail(), student.isMustChangePassword(), student.getDatasource(),
                progress.currentLevel(), progress.completedExercises(), progress.totalExercises());
    }
}
