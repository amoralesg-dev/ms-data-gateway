package com.gateway.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDialectTest {

    private final MySqlDialect dialect = new MySqlDialect();

    @Test
    @DisplayName("Debe reportar tipo MYSQL y soporte nativo de paginación")
    void testEngineTypeAndCapabilities() {
        assertThat(dialect.getEngineType()).isEqualTo(EngineType.MYSQL);
        assertThat(dialect.supportsNativePagination()).isTrue();
        assertThat(dialect.getValidationQuery()).isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("Debe aplicar paginación LIMIT offset, count correctamente")
    void testApplyPagination() {
        // Página 1, 50 registros -> offset 0
        String paged1 = dialect.applyPagination("SELECT id, name FROM users", 1, 50);
        assertThat(paged1).isEqualTo("SELECT id, name FROM users LIMIT 0, 50");

        // Página 3, 20 registros -> offset 40
        String paged3 = dialect.applyPagination("SELECT * FROM orders WHERE status = 'ACTIVE'", 3, 20);
        assertThat(paged3).isEqualTo("SELECT * FROM orders WHERE status = 'ACTIVE' LIMIT 40, 20");

        // Limpieza de punto y coma al final
        String pagedWithSemi = dialect.applyPagination("SELECT * FROM products;", 2, 10);
        assertThat(pagedWithSemi).isEqualTo("SELECT * FROM products LIMIT 10, 10");
    }
}
