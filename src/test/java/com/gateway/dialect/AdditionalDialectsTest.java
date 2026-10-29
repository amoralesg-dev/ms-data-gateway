package com.gateway.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalDialectsTest {

    @Test
    @DisplayName("PostgresDialect: Valida tipo, validación y paginación LIMIT/OFFSET")
    void testPostgresDialect() {
        PostgresDialect dialect = new PostgresDialect();
        assertThat(dialect.getEngineType()).isEqualTo(EngineType.POSTGRESQL);
        assertThat(dialect.supportsNativePagination()).isTrue();
        assertThat(dialect.getValidationQuery()).isEqualTo("SELECT 1");

        String paged1 = dialect.applyPagination("SELECT * FROM users", 1, 10);
        assertThat(paged1).isEqualTo("SELECT * FROM users LIMIT 10 OFFSET 0");

        String paged2 = dialect.applyPagination("SELECT id, name FROM customers;", 3, 25);
        assertThat(paged2).isEqualTo("SELECT id, name FROM customers LIMIT 25 OFFSET 50");
    }

    @Test
    @DisplayName("SqlServerDialect: Valida tipo, validación y paginación OFFSET/FETCH")
    void testSqlServerDialect() {
        SqlServerDialect dialect = new SqlServerDialect();
        assertThat(dialect.getEngineType()).isEqualTo(EngineType.SQLSERVER);
        assertThat(dialect.supportsNativePagination()).isTrue();
        assertThat(dialect.getValidationQuery()).isEqualTo("SELECT 1");

        // Con ORDER BY existente
        String sqlWithOrder = "SELECT OrderID FROM Orders ORDER BY OrderID ASC";
        String paged1 = dialect.applyPagination(sqlWithOrder, 1, 15);
        assertThat(paged1).isEqualTo("SELECT OrderID FROM Orders ORDER BY OrderID ASC OFFSET 0 ROWS FETCH NEXT 15 ROWS ONLY");

        // Sin ORDER BY -> Debe inyectar ORDER BY (SELECT NULL)
        String sqlWithoutOrder = "SELECT * FROM Employees;";
        String paged2 = dialect.applyPagination(sqlWithoutOrder, 2, 20);
        assertThat(paged2).isEqualTo("SELECT * FROM Employees ORDER BY (SELECT NULL) OFFSET 20 ROWS FETCH NEXT 20 ROWS ONLY");
    }

    @Test
    @DisplayName("OracleDialect: Valida tipo, validación DUAL y paginación OFFSET/FETCH")
    void testOracleDialect() {
        OracleDialect dialect = new OracleDialect();
        assertThat(dialect.getEngineType()).isEqualTo(EngineType.ORACLE);
        assertThat(dialect.supportsNativePagination()).isTrue();
        assertThat(dialect.getValidationQuery()).isEqualTo("SELECT 1 FROM DUAL");

        String paged1 = dialect.applyPagination("SELECT ITEM_CODE FROM INVENTORY", 1, 30);
        assertThat(paged1).isEqualTo("SELECT ITEM_CODE FROM INVENTORY OFFSET 0 ROWS FETCH NEXT 30 ROWS ONLY");

        String paged2 = dialect.applyPagination("SELECT * FROM EMPLOYEES;", 4, 10);
        assertThat(paged2).isEqualTo("SELECT * FROM EMPLOYEES OFFSET 30 ROWS FETCH NEXT 10 ROWS ONLY");
    }
}
