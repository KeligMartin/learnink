package com.learnink.grading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionErrorAdvisorTest {

    private final ExecutionErrorAdvisor advisor = new ExecutionErrorAdvisor();

    @Test
    void explainsMissingGroupByColumn() {
        assertThat(advisor.adviceFor("La colonne \"c.numero\" doit être dans la liste du GROUP BY"))
                .singleElement().asString().contains("GROUP BY");
        assertThat(advisor.adviceFor("'residence.c.numero' isn't in GROUP BY")).hasSize(1);
    }

    @Test
    void explainsAggregateInWhere() {
        assertThat(advisor.adviceFor("Utilisation invalide de la fonction agrégée \"COUNT(*)\""))
                .singleElement().asString().contains("HAVING");
        assertThat(advisor.adviceFor("Invalid use of group function")).hasSize(1);
    }

    @Test
    void staysSilentOnOtherErrors() {
        assertThat(advisor.adviceFor("Table \"chambres\" non trouvée")).isEmpty();
        assertThat(advisor.adviceFor(null)).isEmpty();
    }
}
