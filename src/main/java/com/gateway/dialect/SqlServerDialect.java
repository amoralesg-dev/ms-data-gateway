package com.gateway.dialect;

import org.springframework.stereotype.Component;

/**
 * Dialecto para Microsoft SQL Server 2012+.
 * Soporta paginación nativa ANSI SQL:2008 mediante OFFSET x ROWS FETCH NEXT y ROWS ONLY.
 * Requiere que la consulta tenga una cláusula ORDER BY; si no existe, añade ORDER BY (SELECT NULL).
 */
@Component
public class SqlServerDialect implements DatabaseDialect {

    @Override
    public EngineType getEngineType() {
        return EngineType.SQLSERVER;
    }

    @Override
    public String applyPagination(String sql, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        String cleanSql = sql.trim().replaceAll(";+$", "");
        
        // SQL Server requiere ORDER BY para usar OFFSET ... FETCH
        if (!cleanSql.toUpperCase().contains("ORDER BY")) {
            cleanSql = cleanSql + " ORDER BY (SELECT NULL)";
        }
        
        return String.format("%s OFFSET %d ROWS FETCH NEXT %d ROWS ONLY", cleanSql, offset, pageSize);
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
