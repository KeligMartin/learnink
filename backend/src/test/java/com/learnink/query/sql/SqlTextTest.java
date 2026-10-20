package com.learnink.query.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlTextTest {

    @Test
    void stripsCommentsAndLiterals() {
        String sql = "SELECT a -- commentaire\nFROM t /* bloc */ WHERE b = 'It''s SLEEP(' # fin";
        assertThat(SqlText.stripLiteralsAndComments(sql)).isEqualTo("select a  \nfrom t   where b = ''  ");
    }
}
