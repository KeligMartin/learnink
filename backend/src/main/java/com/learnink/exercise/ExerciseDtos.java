package com.learnink.exercise;

import com.learnink.query.QueryEngine;

public final class ExerciseDtos {

    private ExerciseDtos() {
    }

    /** Détail d'un exercice : la solution de référence n'est jamais exposée. */
    public record ExerciseDetailDto(Long id, QueryEngine engine, int level, String levelLabel, int position,
                                    String title, String statement, boolean orderMatters, ChartDto chart,
                                    boolean completed,
                                    Double bestScore, String lastQuery, Long previousExerciseId,
                                    Long nextExerciseId) {
    }

    public record ChartDto(ChartType type, int labelColumns) {

        static ChartDto from(ChartSpec spec) {
            return spec == null ? null : new ChartDto(spec.getType(), spec.getLabelColumns());
        }
    }
}
