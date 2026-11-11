package com.learnink.query.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnink.grading.ComparisonOutcome;
import com.learnink.grading.GradingRequest;
import com.learnink.grading.GradingResult;
import com.learnink.grading.GradingService;
import com.learnink.grading.ResultComparator;
import com.learnink.query.QueryEngine;
import com.learnink.query.QueryExecutionStrategyRegistry;
import com.learnink.query.QueryResult;
import com.learnink.query.QueryTarget;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie, sur un MongoDB embarqué chargé avec les jeux de données, que chaque solution de référence MongoDB :
 * s'exécute, obtient 10/10, et renvoie le même résultat que l'exercice SQL équivalent (mêmes données).
 * <p>
 * Lancement : {@code mvn test -Dmongo-it=true -Dtest=MongoReferenceSolutionsTest} (télécharge mongod au premier lancement).
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "mongo-it", matches = "true")
class MongoReferenceSolutionsTest {

    /** Exercices MongoDB dont l'énoncé diffère volontairement de l'exercice SQL (noms affichés au lieu d'identifiants). */
    private static final Set<String> NOT_COMPARABLE = Set.of("resm-1-05");

    private static TransitionWalker.ReachedState<RunningMongodProcess> mongod;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (mongod == null) {
            mongod = Mongod.instance().start(Version.Main.V7_0);
        }
        var address = mongod.current().getServerAddress();
        String base = "mongodb://" + address.getHost() + ":" + address.getPort() + "/";
        registry.add("learnink.mongo.datasets.1.uri", () -> base + "learnink_residence");
        registry.add("learnink.mongo.datasets.2.uri", () -> base + "learnink_transport");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:app-mongo-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @AfterAll
    static void stop() {
        if (mongod != null) {
            mongod.close();
        }
    }

    @Autowired
    private GradingService gradingService;
    @Autowired
    private QueryExecutionStrategyRegistry strategies;
    @Autowired
    private ResultComparator comparator;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void residence() throws IOException {
        check("seed/exercises-residence-mongodb.json", "seed/exercises-residence.json", "resm-", "res-");
    }

    @Test
    void transport() throws IOException {
        if (!new ClassPathResource("seed/exercises-transport-mongodb.json").exists()) {
            return;
        }
        check("seed/exercises-transport-mongodb.json", "seed/exercises-transport.json", "tram-", "tra-");
    }

    private void check(String mongoFile, String sqlFile, String mongoPrefix, String sqlPrefix) throws IOException {
        Map<String, JsonNode> sql = new HashMap<>();
        read(sqlFile).forEach(node -> sql.put(node.get("code").asText(), node));
        List<String> failures = new ArrayList<>();
        List<JsonNode> exercises = read(mongoFile);
        for (JsonNode exercise : exercises) {
            String code = exercise.get("code").asText();
            String reference = exercise.get("referenceQuery").asText();
            boolean orderMatters = exercise.get("orderMatters").asBoolean();
            QueryTarget target = QueryTarget.system(exercise.get("datasource").asInt());
            try {
                QueryResult mongo = strategies.get(QueryEngine.MONGODB).execute(reference, target);
                if (mongo.rowCount() == 0) {
                    failures.add(code + " : aucune ligne");
                    continue;
                }
                GradingResult graded = gradingService.grade(
                        new GradingRequest(QueryEngine.MONGODB, target, reference, orderMatters, reference));
                if (graded.report() == null || graded.report().score() < 10) {
                    failures.add(code + " : note " + (graded.report() == null ? graded : graded.report()));
                }
                JsonNode twin = sql.get(code.replace(mongoPrefix, sqlPrefix));
                if (twin != null && !NOT_COMPARABLE.contains(code)) {
                    QueryResult expected = strategies.get(QueryEngine.SQL).execute(twin.get("referenceQuery").asText(), target);
                    ComparisonOutcome outcome = comparator.compare(expected, mongo, orderMatters);
                    if (!outcome.identical()) {
                        failures.add(code + " : différent du SQL " + outcome.hints() + "\n    SQL   " + expected.columns() + " "
                                + head(expected) + "\n    Mongo " + mongo.columns() + " " + head(mongo));
                    }
                }
                System.out.printf("%s OK (%d lignes, %.1f ms)%n", code, mongo.rowCount(), mongo.executionTimeNanos() / 1e6);
            } catch (RuntimeException e) {
                failures.add(code + " : " + e.getMessage());
            }
        }
        assertThat(failures).as("Solutions de référence MongoDB").isEmpty();
        assertThat(exercises).hasSize(26);
    }

    private static List<List<Object>> head(QueryResult result) {
        return result.rows().stream().limit(4).toList();
    }

    private List<JsonNode> read(String path) throws IOException {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            List<JsonNode> nodes = new ArrayList<>();
            json.readTree(input).forEach(nodes::add);
            return nodes;
        }
    }
}
