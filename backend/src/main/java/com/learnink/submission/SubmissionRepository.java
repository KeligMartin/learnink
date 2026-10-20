package com.learnink.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    interface ExerciseStats {
        Long getExerciseId();

        Double getBestScore();

        Number getCompleted();

        Number getAttempts();
    }

    @Query("""
            select s.exercise.id as exerciseId,
                   max(s.score) as bestScore,
                   max(case when s.correct = true then 1 else 0 end) as completed,
                   count(s) as attempts
            from Submission s
            where s.user.id = :userId
            group by s.exercise.id
            """)
    List<ExerciseStats> findStatsByUserId(@Param("userId") Long userId);

    List<Submission> findTop20ByUser_IdAndExercise_IdOrderByCreatedAtDesc(Long userId, Long exerciseId);

    Optional<Submission> findFirstByUser_IdAndExercise_IdOrderByCreatedAtDesc(Long userId, Long exerciseId);
}
