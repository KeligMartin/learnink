package com.learnink.submission;

import com.learnink.grading.ScoreCriterion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SubmissionDtos {

    private SubmissionDtos() {
    }

    public record SubmissionRequest(@NotBlank @Size(max = 10_000) String query) {
    }

    /**
     * @param totalRows   nombre de lignes renvoyées par la requête
     * @param tooManyRows vrai si la requête dépasse la limite de lignes lues
     */
    public record QueryResultDto(List<String> columns, List<List<Object>> rows, int totalRows,
                                 boolean tooManyRows, double executionTimeMs) {
    }

    public record SubmissionResultDto(Long submissionId, double score, double maxScore, boolean correct,
                                      List<ScoreCriterion> criteria, List<String> hints, QueryResultDto result,
                                      String error, boolean levelCompleted, Long nextExerciseId) {
    }

    public record SubmissionHistoryDto(Long id, String query, double score, boolean correct, Instant createdAt) {

        static SubmissionHistoryDto from(Submission submission) {
            return new SubmissionHistoryDto(submission.getId(), submission.getQuery(), submission.getScore(),
                    submission.isCorrect(), submission.getCreatedAt());
        }
    }
}
