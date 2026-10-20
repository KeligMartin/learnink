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
}
