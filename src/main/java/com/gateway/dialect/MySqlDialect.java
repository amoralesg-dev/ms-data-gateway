package com.gateway.dialect;

import org.springframework.stereotype.Component;

/**
 * Dialecto para MySQL 5.7+ y 8.x.
 * Soporta paginación eficiente con LIMIT offset, count.
 */
@Component
public class MySqlDialect implements DatabaseDialect {

    @Override
    public EngineType getEngineType() {
        return EngineType.MYSQL;
    }

    @Override
    public String applyPagination(String sql, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        // Limpiar punto y coma al final si existiera
        String cleanSql = sql.trim().replaceAll(";+$", "");
        return String.format("%s LIMIT %d, %d", cleanSql, offset, pageSize);
    }

    @Override
    public String getValidationQuery() {
        return "SELECT 1";
    }

    @Override
    public boolean supportsNativePagination() {
        return true;
    }
}
