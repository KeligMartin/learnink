package com.learnink.progress;

import com.learnink.query.QueryEngine;

import java.util.List;

public final class ProgressDtos {

    private ProgressDtos() {
    }

    public record ExerciseSummaryDto(Long id, int level, int position, String title, boolean completed,
                                     Double bestScore, long attempts) {
    }

    public record LevelDto(int level, String label, boolean unlocked, boolean completed,
                           List<ExerciseSummaryDto> exercises) {
    }

    public record ProgressDto(QueryEngine engine, int currentLevel, int completedExercises, int totalExercises,
                              List<LevelDto> levels) {

        public List<ExerciseSummaryDto> orderedExercises() {
            return levels.stream().flatMap(level -> level.exercises().stream()).toList();
        }
    }
}
