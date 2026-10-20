package com.learnink.exercise;

import com.learnink.query.QueryEngine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByEngineAndDatasourceAndArchivedFalseOrderByLevelAscPositionAsc(QueryEngine engine, int datasource);

    List<Exercise> findByArchivedFalse();

    Optional<Exercise> findByIdAndArchivedFalse(Long id);

    Optional<Exercise> findByCode(String code);
}
