package com.gateway.dialect;

import org.springframework.stereotype.Component;

/**
 * Dialecto para Progress OpenEdge DataDirect JDBC.
 * Debido a variaciones de sintaxis entre versiones de Progress,
 * la paginación se aplica de forma segura en memoria a nivel JDBC.
 */
@Component
public class OpenEdgeDialect implements DatabaseDialect {

    @Override
    public EngineType getEngineType() {
        return EngineType.OPENEDGE;
    }

    @Override
    public String applyPagination(String sql, int page, int pageSize) {
        // En OpenEdge se retorna la query intacta para corte en memoria vía RowCallbackHandler
        return sql;
    }

    @Override
    public String getValidationQuery() {
        return "SELECT 1 FROM sysprogress.SYSTABLES WHERE TBL = 'SYSTABLES'";
    }

    @Override
    public boolean supportsNativePagination() {
        return false;
    }
}
