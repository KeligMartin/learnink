package com.learnink.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPasswordGeneratorTest {

    private final DefaultPasswordGenerator generator = new DefaultPasswordGenerator("2026");

    @Test
    void usesTheLocalPartOfTheEmailFollowedByTheSuffix() {
        assertThat(generator.generate("plancelet@ecole.fr")).isEqualTo("plancelet2026");
    }

    @Test
    void ignoresCaseAndSurroundingSpaces() {
        assertThat(generator.generate("  Jean.Dupont@Ecole.fr ")).isEqualTo("jean.dupont2026");
    }
}
