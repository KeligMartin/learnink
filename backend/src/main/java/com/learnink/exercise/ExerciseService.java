package com.learnink.exercise;

import com.learnink.common.ResourceNotFoundException;
import com.learnink.exercise.ExerciseDtos.ChartDto;
import com.learnink.exercise.ExerciseDtos.ExerciseDetailDto;
import com.learnink.progress.Levels;
import com.learnink.progress.ProgressDtos.ExerciseSummaryDto;
import com.learnink.progress.ProgressDtos.ProgressDto;
import com.learnink.progress.ProgressService;
import com.learnink.security.AuthenticatedUser;
import com.learnink.submission.Submission;
import com.learnink.submission.SubmissionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final SubmissionRepository submissionRepository;
    private final ProgressService progressService;

    public ExerciseService(ExerciseRepository exerciseRepository, SubmissionRepository submissionRepository,
                           ProgressService progressService) {
        this.exerciseRepository = exerciseRepository;
        this.submissionRepository = submissionRepository;
        this.progressService = progressService;
    }

    public Exercise findExercise(Long exerciseId) {
        return exerciseRepository.findByIdAndArchivedFalse(exerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercice introuvable."));
    }

    public ExerciseDetailDto detail(AuthenticatedUser user, Long exerciseId) {
        Exercise exercise = findExercise(exerciseId);
        ProgressDto progress = progressService.progress(user, exercise.getEngine());
        progressService.assertAccessible(progress, exercise);

        List<ExerciseSummaryDto> ordered = progress.orderedExercises();
        int index = indexOf(ordered, exerciseId);
        ExerciseSummaryDto summary = ordered.get(index);
        Long previousId = index > 0 ? ordered.get(index - 1).id() : null;
        Long nextId = progressService.nextAccessibleExerciseId(progress, exerciseId);
        String lastQuery = submissionRepository
                .findFirstByUser_IdAndExercise_IdOrderByCreatedAtDesc(user.id(), exerciseId)
                .map(Submission::getQuery)
                .orElse(null);

        return new ExerciseDetailDto(exercise.getId(), exercise.getEngine(), exercise.getLevel(),
                Levels.label(exercise.getLevel()), exercise.getPosition(), exercise.getTitle(),
                exercise.getStatement(), exercise.isOrderMatters(), ChartDto.from(exercise.getChart()),
                summary.completed(), summary.bestScore(), lastQuery, previousId, nextId);
    }

    private static int indexOf(List<ExerciseSummaryDto> exercises, Long exerciseId) {
        for (int i = 0; i < exercises.size(); i++) {
            if (exercises.get(i).id().equals(exerciseId)) {
                return i;
            }
        }
        throw new ResourceNotFoundException("Exercice introuvable.");
    }
}
