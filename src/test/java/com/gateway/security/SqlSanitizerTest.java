package com.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlSanitizerTest {

    @Test
    @DisplayName("Debe permitir sentencias SELECT válidas")
    void testValidSelectQueries() {
        assertThatCode(() -> SqlSanitizer.validateReadOnlySelect("SELECT * FROM SYSPROGRESS.SYSTABLES"))
                .doesNotThrowAnyException();

        assertThatCode(() -> SqlSanitizer.validateReadOnlySelect("  select col1, col2 from my_table where id = 10  "))
                .doesNotThrowAnyException();

        assertThatCode(() -> SqlSanitizer.validateReadOnlySelect("WITH cte AS (SELECT id FROM users) SELECT * FROM cte"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE pub.addr SET name = 'Hacked' WHERE id = 1",
            "DELETE FROM pub.addr WHERE id = 1",
            "DROP TABLE pub.addr",
            "INSERT INTO pub.addr (id) VALUES (1)",
            "TRUNCATE TABLE pub.addr",
            "ALTER TABLE pub.addr ADD COLUMN x INT",
            "CREATE TABLE hack (id INT)",
            "GRANT ALL PRIVILEGES ON pub.addr TO user",
            "REVOKE ALL PRIVILEGES ON pub.addr FROM user",
            "MERGE INTO target USING source ON (id) WHEN MATCHED THEN UPDATE",
            "EXEC sp_my_proc",
            "CALL my_procedure()"
    })
    @DisplayName("Debe bloquear categóricamente sentencias DML y DDL destructivas")
    void testBlockedDmlAndDdl(String sql) {
        assertThatThrownBy(() -> SqlSanitizer.validateReadOnlySelect(sql))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Operación no autorizada");
    }

    @Test
    @DisplayName("Debe bloquear múltiples sentencias separadas por punto y coma")
    void testBlockedMultipleStatements() {
        String attack = "SELECT * FROM users; DROP TABLE logs";
        assertThatThrownBy(() -> SqlSanitizer.validateReadOnlySelect(attack))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Múltiples sentencias");
    }

    @Test
    @DisplayName("Debe bloquear comentarios SQL sospechosos")
    void testBlockedComments() {
        String commentAttack1 = "SELECT * FROM users -- bypass rest";
        assertThatThrownBy(() -> SqlSanitizer.validateReadOnlySelect(commentAttack1))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Comentarios SQL");

        String commentAttack2 = "SELECT * FROM users /* inline comment */";
        assertThatThrownBy(() -> SqlSanitizer.validateReadOnlySelect(commentAttack2))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Comentarios SQL");
    }

    @Test
    @DisplayName("Debe fallar si el SQL está vacío o es nulo")
    void testEmptySql() {
        assertThatThrownBy(() -> SqlSanitizer.validateReadOnlySelect(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSanitizer.validateReadOnlySelect(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
