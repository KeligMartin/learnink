package com.learnink.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnink.exercise.ChartSpec;
import com.learnink.exercise.ChartType;
import com.learnink.exercise.Exercise;
import com.learnink.exercise.ExerciseRepository;
import com.learnink.query.EngineUnavailableException;
import com.learnink.query.QueryEngine;
import com.learnink.query.QueryExecutionStrategy;
import com.learnink.query.QueryExecutionStrategyRegistry;
import com.learnink.query.QueryTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.learnink.startup.StartupOrder;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Charge (ou met à jour, par code) les exercices décrits dans {@code seed/exercises-*.json}
 * et vérifie que chaque solution de référence s'exécute.
 */
@Component
@Order(StartupOrder.EXERCISES)
@ConditionalOnProperty(name = "learnink.seed.exercises", havingValue = "true")
public class ExerciseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExerciseSeeder.class);

    record ChartSeed(ChartType type, Integer labelColumns) {
    }

    /** @param datasource source de données de l'exercice (1 si absent du fichier) */
    record ExerciseSeed(String code, QueryEngine engine, Integer datasource, int level, int position, String title,
                        String statement, String referenceQuery, boolean orderMatters, ChartSeed chart) {

        int datasourceOrDefault() {
            return datasource == null ? 1 : datasource;
        }
    }

    private final ExerciseRepository exerciseRepository;
    private final QueryExecutionStrategyRegistry strategies;
    private final ObjectMapper objectMapper;

    public ExerciseSeeder(ExerciseRepository exerciseRepository, QueryExecutionStrategyRegistry strategies,
                          ObjectMapper objectMapper) {
        this.exerciseRepository = exerciseRepository;
        this.strategies = strategies;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:seed/exercises-*.json");
        Set<String> codes = new HashSet<>();
        for (Resource resource : resources) {
            try (InputStream input = resource.getInputStream()) {
                List<ExerciseSeed> seeds = objectMapper.readValue(input, new TypeReference<>() {
                });
                seeds.forEach(this::upsert);
                seeds.forEach(seed -> codes.add(seed.code()));
                log.info("{} exercice(s) chargé(s) depuis {}", seeds.size(), resource.getFilename());
            }
        }
        archiveRemovedExercises(codes);
    }

    /** Les exercices retirés des fichiers sont archivés (et non supprimés) pour conserver les tentatives. */
    private void archiveRemovedExercises(Set<String> codes) {
        exerciseRepository.findByArchivedFalse().stream()
                .filter(exercise -> !codes.contains(exercise.getCode()))
                .forEach(exercise -> {
                    exercise.archive();
                    log.info("Exercice {} archivé : il n'est plus présent dans les fichiers de seed", exercise.getCode());
                });
    }

    private void upsert(ExerciseSeed seed) {
        Exercise exercise = exerciseRepository.findByCode(seed.code()).orElseGet(() -> new Exercise(seed.code()));
        ChartSpec chart = seed.chart() == null ? null : new ChartSpec(seed.chart().type(), seed.chart().labelColumns());
        exercise.update(seed.engine(), seed.datasourceOrDefault(), seed.level(), seed.position(), seed.title(),
                seed.statement(),
                seed.referenceQuery(), seed.orderMatters(), chart);
        exerciseRepository.save(exercise);
        checkReferenceQuery(seed);
    }

    private void checkReferenceQuery(ExerciseSeed seed) {
        strategies.all().stream()
                .filter(strategy -> strategy.engine() == seed.engine() && strategy.isAvailable())
                .findFirst()
                .ifPresent(strategy -> execute(strategy, seed));
    }

    private static void execute(QueryExecutionStrategy strategy, ExerciseSeed seed) {
        try {
            QueryTarget target = QueryTarget.system(seed.datasourceOrDefault());
            if (strategy.execute(seed.referenceQuery(), target).rowCount() == 0) {
                log.warn("La solution de référence de l'exercice {} ne renvoie aucune ligne.", seed.code());
            }
        } catch (EngineUnavailableException e) {
            log.warn("Exercice {} : source de données n°{} non configurée, solution non vérifiée",
                    seed.code(), seed.datasourceOrDefault());
        } catch (RuntimeException e) {
            log.error("La solution de référence de l'exercice {} ne s'exécute pas : {}", seed.code(), e.getMessage());
        }
    }
}
