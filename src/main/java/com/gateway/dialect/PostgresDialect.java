package com.gateway.dialect;

import org.springframework.stereotype.Component;

/**
 * Dialecto para PostgreSQL 10+.
 * Soporta paginación nativa con LIMIT y OFFSET.
 */
@Component
public class PostgresDialect implements DatabaseDialect {

    @Override
    public EngineType getEngineType() {
        return EngineType.POSTGRESQL;
    }

    @Override
    public String applyPagination(String sql, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        String cleanSql = sql.trim().replaceAll(";+$", "");
        return String.format("%s LIMIT %d OFFSET %d", cleanSql, pageSize, offset);
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
