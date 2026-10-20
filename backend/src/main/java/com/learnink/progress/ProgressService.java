package com.learnink.progress;

import com.learnink.common.ResourceNotFoundException;
import com.learnink.exercise.Exercise;
import com.learnink.exercise.ExerciseRepository;
import com.learnink.progress.ProgressDtos.ExerciseSummaryDto;
import com.learnink.progress.ProgressDtos.LevelDto;
import com.learnink.progress.ProgressDtos.ProgressDto;
import com.learnink.query.QueryEngine;
import com.learnink.security.AuthenticatedUser;
import com.learnink.submission.SubmissionRepository;
import com.learnink.submission.SubmissionRepository.ExerciseStats;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcule la progression d'un étudiant : un niveau n'est déverrouillé que lorsque
 * tous les exercices du niveau précédent ont été réussis (résultat exact).
 * Les enseignants ont accès à tous les niveaux.
 */
@Service
public class ProgressService {

    private final ExerciseRepository exerciseRepository;
    private final SubmissionRepository submissionRepository;

    public ProgressService(ExerciseRepository exerciseRepository, SubmissionRepository submissionRepository) {
        this.exerciseRepository = exerciseRepository;
        this.submissionRepository = submissionRepository;
    }

    public ProgressDto progress(AuthenticatedUser user, QueryEngine engine) {
        return progress(user.id(), user.isTeacher(), engine, user.datasource());
    }

    /** Progression sur les exercices de la source de données de l'utilisateur. */
    public ProgressDto progress(Long userId, boolean unlockAll, QueryEngine engine, int datasource) {
        Map<Long, ExerciseStats> stats = submissionRepository.findStatsByUserId(userId).stream()
                .collect(Collectors.toMap(ExerciseStats::getExerciseId, Function.identity()));
        Map<Integer, List<Exercise>> exercisesByLevel = exerciseRepository.findByEngineAndDatasourceAndArchivedFalseOrderByLevelAscPositionAsc(engine, datasource)
                .stream()
                .collect(Collectors.groupingBy(Exercise::getLevel, TreeMap::new, Collectors.toList()));

        List<LevelDto> levels = new ArrayList<>();
        boolean previousLevelCompleted = true;
        for (Map.Entry<Integer, List<Exercise>> entry : exercisesByLevel.entrySet()) {
            List<ExerciseSummaryDto> exercises = entry.getValue().stream()
                    .map(exercise -> summary(exercise, stats.get(exercise.getId())))
                    .toList();
            boolean completed = exercises.stream().allMatch(ExerciseSummaryDto::completed);
            boolean unlocked = unlockAll || previousLevelCompleted;
            levels.add(new LevelDto(entry.getKey(), Levels.label(entry.getKey()), unlocked, completed, exercises));
            previousLevelCompleted = completed;
        }

        int currentLevel = levels.stream()
                .filter(level -> level.unlocked() && !level.completed())
                .mapToInt(LevelDto::level)
                .findFirst()
                .orElse(levels.isEmpty() ? 1 : levels.getLast().level());
        int completedCount = (int) levels.stream().flatMap(l -> l.exercises().stream())
                .filter(ExerciseSummaryDto::completed).count();
        int total = levels.stream().mapToInt(l -> l.exercises().size()).sum();
        return new ProgressDto(engine, currentLevel, completedCount, total, levels);
    }

    public void assertAccessible(ProgressDto progress, Exercise exercise) {
        boolean inPath = progress.orderedExercises().stream().anyMatch(e -> e.id().equals(exercise.getId()));
        if (!inPath) {
            // Exercice d'une autre source de données que celle de l'utilisateur
            throw new ResourceNotFoundException("Exercice introuvable.");
        }
        boolean unlocked = progress.levels().stream()
                .anyMatch(level -> level.level() == exercise.getLevel() && level.unlocked());
        if (!unlocked) {
            throw new LevelLockedException(exercise.getLevel());
        }
    }

    /** Exercice suivant dans l'ordre du parcours, s'il est accessible. */
    public Long nextAccessibleExerciseId(ProgressDto progress, Long exerciseId) {
        boolean found = false;
        for (LevelDto level : progress.levels()) {
            for (ExerciseSummaryDto exercise : level.exercises()) {
                if (found) {
                    return level.unlocked() ? exercise.id() : null;
                }
                found = exercise.id().equals(exerciseId);
            }
        }
        return null;
    }

    public boolean isLevelCompleted(ProgressDto progress, int level) {
        return progress.levels().stream().anyMatch(l -> l.level() == level && l.completed());
    }

    private static ExerciseSummaryDto summary(Exercise exercise, ExerciseStats stats) {
        boolean completed = stats != null && stats.getCompleted().intValue() > 0;
        Double bestScore = stats == null ? null : stats.getBestScore();
        long attempts = stats == null ? 0 : stats.getAttempts().longValue();
        return new ExerciseSummaryDto(exercise.getId(), exercise.getLevel(), exercise.getPosition(),
                exercise.getTitle(), completed, bestScore, attempts);
    }
}
