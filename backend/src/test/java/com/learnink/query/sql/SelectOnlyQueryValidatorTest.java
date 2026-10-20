package com.learnink.query.sql;

import com.learnink.query.QueryRejectedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelectOnlyQueryValidatorTest {

    private final SelectOnlyQueryValidator validator = new SelectOnlyQueryValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT id, nom FROM batiment",
            "select * from plat where nom = 'sleep(10)'",
            "WITH g AS (SELECT id FROM batiment) SELECT id FROM g",
            "SELECT a FROM t UNION SELECT b FROM u",
    })
    void acceptsReadQueries(String query) {
        assertThat(validator.validate(query)).isNotBlank();
    }

    @Test
    void removesTrailingSemicolons() {
        assertThat(validator.validate("SELECT 1 ;; ")).isEqualTo("SELECT 1");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "DELETE FROM batiment",
            "UPDATE plat SET nom = 'x'",
            "INSERT INTO batiment VALUES (9, 'x')",
            "DROP TABLE plat",
            "SELECT 1; DROP TABLE plat",
            "SELECT SLEEP(5)",
            "SELECT BENCHMARK(1000000, MD5('a'))",
            "SELECT * FROM plat INTO OUTFILE '/tmp/x'",
            "SELECT * FROM plat FOR UPDATE",
            "SELEC id FROM batiment",
    })
    void rejectsEverythingElse(String query) {
        assertThatThrownBy(() -> validator.validate(query)).isInstanceOf(QueryRejectedException.class);
    }

    @Test
    void explainsSyntaxErrorsInFrench() {
        assertThatThrownBy(() -> validator.validate("SELEC id FROM batiment"))
                .hasMessage("Erreur de syntaxe SQL : élément inattendu « SELEC » (ligne 1, colonne 1).");
        assertThatThrownBy(() -> validator.validate("SELECT id FROM batiment WHERE"))
                .hasMessage("Erreur de syntaxe SQL : élément inattendu « WHERE » (ligne 1, colonne 25).");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT email, password_hash FROM app_user",
            "SELECT * FROM APP_USER",
            "SELECT reference_query FROM `exercise`",
            "SELECT nom FROM batiment WHERE id IN (SELECT user_id FROM submission)",
            "SELECT * FROM information_schema.tables",
            "SELECT user, authentication_string FROM mysql.user",
            "SELECT * FROM learnink.flyway_schema_history",
    })
    void rejectsApplicationAndSystemTables(String query) {
        assertThatThrownBy(() -> validator.validate(query))
                .isInstanceOf(QueryRejectedException.class)
                .hasMessageContaining("n'est pas accessible");
    }

    @Test
    void acceptsBusinessTablesAndCommonTableExpressions() {
        assertThat(validator.validate(
                "WITH stats AS (SELECT batiment_id, COUNT(*) AS n FROM chambre GROUP BY batiment_id) "
                        + "SELECT b.nom, s.n FROM batiment b JOIN stats s ON s.batiment_id = b.id")).isNotBlank();
    }
}
