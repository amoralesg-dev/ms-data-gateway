package com.gateway.dialect;

import org.springframework.stereotype.Component;

/**
 * Dialecto para Oracle Database 12c+.
 * Soporta paginación nativa mediante OFFSET x ROWS FETCH NEXT y ROWS ONLY.
 */
@Component
public class OracleDialect implements DatabaseDialect {

    @Override
    public EngineType getEngineType() {
        return EngineType.ORACLE;
    }

    @Override
    public String applyPagination(String sql, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        String cleanSql = sql.trim().replaceAll(";+$", "");
        return String.format("%s OFFSET %d ROWS FETCH NEXT %d ROWS ONLY", cleanSql, offset, pageSize);
    }

    @Override
    public String getValidationQuery() {
        return "SELECT 1 FROM DUAL";
    }

    @Override
    public boolean supportsNativePagination() {
        return true;
    }
}
