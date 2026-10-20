package com.learnink.submission;

import com.learnink.exercise.Exercise;
import com.learnink.exercise.ExerciseService;
import com.learnink.grading.GradingRequest;
import com.learnink.grading.GradingResult;
import com.learnink.grading.GradingService;
import com.learnink.grading.ScoreReport;
import com.learnink.progress.ProgressDtos.ProgressDto;
import com.learnink.progress.ProgressService;
import com.learnink.query.QueryResult;
import com.learnink.query.QueryTarget;
import com.learnink.query.sql.SandboxProperties;
import com.learnink.security.AuthenticatedUser;
import com.learnink.submission.SubmissionDtos.QueryResultDto;
import com.learnink.submission.SubmissionDtos.SubmissionHistoryDto;
import com.learnink.submission.SubmissionDtos.SubmissionResultDto;
import com.learnink.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Corrige une requête, enregistre la tentative et renvoie le retour en direct à l'étudiant.
 * Volontairement non transactionnel : la correction peut durer plusieurs secondes
 * et ne doit pas monopoliser une connexion à la base applicative.
 */
@Service
public class SubmissionService {

    private final ExerciseService exerciseService;
    private final ProgressService progressService;
    private final GradingService gradingService;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final SandboxProperties sandboxProperties;

    public SubmissionService(ExerciseService exerciseService, ProgressService progressService,
                             GradingService gradingService, SubmissionRepository submissionRepository,
                             UserRepository userRepository, SandboxProperties sandboxProperties) {
        this.exerciseService = exerciseService;
        this.progressService = progressService;
        this.gradingService = gradingService;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.sandboxProperties = sandboxProperties;
    }

    public SubmissionResultDto submit(AuthenticatedUser user, Long exerciseId, String query) {
        Exercise exercise = exerciseService.findExercise(exerciseId);
        ProgressDto before = progressService.progress(user, exercise.getEngine());
        progressService.assertAccessible(before, exercise);

        GradingResult grading = gradingService.grade(new GradingRequest(exercise.getEngine(), QueryTarget.of(user),
                exercise.getReferenceQuery(), exercise.isOrderMatters(), query));
        ScoreReport report = grading.report();
        Double executionTimeMs = grading.result() == null ? null : toMillis(grading.result().executionTimeNanos());

        Submission submission = submissionRepository.save(new Submission(
                userRepository.getReferenceById(user.id()), exercise, query, report.score(), report.correct(),
                executionTimeMs));

        ProgressDto after = progressService.progress(user, exercise.getEngine());
        boolean levelCompleted = progressService.isLevelCompleted(after, exercise.getLevel())
                && !progressService.isLevelCompleted(before, exercise.getLevel());

        return new SubmissionResultDto(submission.getId(), report.score(), ScoreReport.MAX_SCORE, report.correct(),
                report.criteria(), report.hints(), toDto(grading.result()), grading.error(), levelCompleted,
                progressService.nextAccessibleExerciseId(after, exerciseId));
    }

    public List<SubmissionHistoryDto> history(AuthenticatedUser user, Long exerciseId) {
        return submissionRepository.findTop20ByUser_IdAndExercise_IdOrderByCreatedAtDesc(user.id(), exerciseId)
                .stream()
                .map(SubmissionHistoryDto::from)
                .toList();
    }

    private QueryResultDto toDto(QueryResult result) {
        if (result == null) {
            return null;
        }
        List<List<Object>> displayed = result.rows().stream().limit(sandboxProperties.maxDisplayedRows()).toList();
        return new QueryResultDto(result.columns(), displayed, result.rowCount(), result.truncated(),
                toMillis(result.executionTimeNanos()));
    }

    private static double toMillis(long nanos) {
        return Math.round(nanos / 10_000.0) / 100.0;
    }
}
