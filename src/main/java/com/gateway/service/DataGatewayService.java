package com.gateway.service;

import com.gateway.config.GatewayProperties;
import com.gateway.dto.OperationRequest;
import com.gateway.dto.QueryRequest;
import com.gateway.dto.TransactionRequest;
import com.gateway.model.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DataGatewayService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final GatewayProperties gatewayProperties;
    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    private final Optional<com.gateway.catalog.GatewayCatalogRepository> catalogRepository;
    private final Optional<DynamicDataSourceRouter> dynamicDataSourceRouter;

    @org.springframework.beans.factory.annotation.Autowired
    public DataGatewayService(JdbcTemplate jdbcTemplate,
                              NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                              GatewayProperties gatewayProperties,
                              MeterRegistry meterRegistry,
                              DataSource dataSource,
                              Optional<com.gateway.catalog.GatewayCatalogRepository> catalogRepository,
                              Optional<DynamicDataSourceRouter> dynamicDataSourceRouter) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.gatewayProperties = gatewayProperties;
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        this.catalogRepository = catalogRepository != null ? catalogRepository : Optional.empty();
        this.dynamicDataSourceRouter = dynamicDataSourceRouter != null ? dynamicDataSourceRouter : Optional.empty();
    }

    public DataGatewayService(JdbcTemplate jdbcTemplate,
                              NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                              GatewayProperties gatewayProperties,
                              MeterRegistry meterRegistry,
                              DataSource dataSource,
                              Optional<com.gateway.catalog.GatewayCatalogRepository> catalogRepository) {
        this(jdbcTemplate, namedParameterJdbcTemplate, gatewayProperties, meterRegistry, dataSource, catalogRepository, Optional.empty());
    }

    public DataGatewayService(JdbcTemplate jdbcTemplate,
                              NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                              GatewayProperties gatewayProperties,
                              MeterRegistry meterRegistry,
                              DataSource dataSource) {
        this(jdbcTemplate, namedParameterJdbcTemplate, gatewayProperties, meterRegistry, dataSource, Optional.empty(), Optional.empty());
    }

    public QueryResult executeRegisteredQuery(QueryRequest request) {
        return executeRegisteredQuery(request, "API_CLIENT", "127.0.0.1");
    }

    public QueryResult executeRegisteredQuery(QueryRequest request, String executedBy, String clientIp) {
        long startTime = System.currentTimeMillis();
        String queryId = request.getQueryId();
        
        log.info("Executing registered query: queryId={}, params={}", queryId, request.getParams());
        
        String sql = null;
        String queryType = "SELECT";
        int configuredMaxRows = gatewayProperties.getQueries().getMaxRows();
        String datasourceId = "openedge-mfg";

        // 1. Intentar resolver desde catálogo persistente (MySQL)
        if (catalogRepository.isPresent()) {
            try {
                var optEntity = catalogRepository.get().findQueryByCode(queryId);
                if (optEntity.isPresent()) {
                    var entity = optEntity.get();
                    if (!entity.isEnabled()) {
                        return QueryResult.error("QUERY_DISABLED", "La consulta '" + queryId + "' está deshabilitada en el catálogo");
                    }
                    sql = entity.getSqlText();
                    queryType = entity.getQueryType();
                    configuredMaxRows = entity.getMaxRows() > 0 ? entity.getMaxRows() : configuredMaxRows;
                    datasourceId = entity.getDatasourceId();
                }
            } catch (Exception ex) {
                log.debug("Error consultando catálogo persistente, recurriendo a configuración YAML: {}", ex.getMessage());
            }
        }

        // 2. Si no se encontró en base de datos, recurrir a configuración YAML (compatibilidad hacia atrás)
        if (sql == null) {
            GatewayProperties.RegisteredQuery registeredQuery = gatewayProperties.getRegisteredQueries().get(queryId);
            if (registeredQuery != null) {
                sql = registeredQuery.getSql();
                queryType = registeredQuery.getType();
            }
        }

        if (sql == null) {
            return QueryResult.error("QUERY_NOT_FOUND", "Query not found: " + queryId);
        }
        if (!"SELECT".equalsIgnoreCase(queryType)) {
            return QueryResult.error("INVALID_QUERY_TYPE", "Query '" + queryId + "' is not a SELECT query");
        }
        
        try {
            MapSqlParameterSource paramSource = new MapSqlParameterSource();
            if (request.getParams() != null) {
                request.getParams().forEach(paramSource::addValue);
            }
            
            int maxRows = configuredMaxRows;
            int page = request.getPage() != null ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 
                           gatewayProperties.getQueries().getDefaultPageSize();
            
            // Fetch all rows up to maxRows (OpenEdge may not support OFFSET/FETCH)
            List<Map<String, Object>> allResults = new ArrayList<>();
            List<ColumnInfo> columns = new ArrayList<>();
            
            final String targetDs = datasourceId;
            NamedParameterJdbcTemplate npjt = dynamicDataSourceRouter
                    .map(router -> router.getNamedParameterJdbcTemplate(targetDs))
                    .orElse(this.namedParameterJdbcTemplate);

            npjt.query(sql, paramSource, rs -> {
                // Extract column metadata on first row
                if (columns.isEmpty()) {
                    columns.addAll(extractColumns(rs.getMetaData()));
                }
                if (allResults.size() < maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        Object val = com.gateway.util.JdbcValueFormatter.formatValue(rs.getObject(i));
                        row.put(meta.getColumnLabel(i), val);
                    }
                    allResults.add(row);
                }
            });
            
            // Apply in-memory pagination
            int totalRows = allResults.size();
            int totalPages = (int) Math.ceil((double) totalRows / pageSize);
            int fromIndex = Math.min((page - 1) * pageSize, totalRows);
            int toIndex = Math.min(fromIndex + pageSize, totalRows);
            List<Map<String, Object>> pagedResults = allResults.subList(fromIndex, toIndex);
            
            List<List<Object>> rows = buildRows(pagedResults, columns);
            
            long executionTimeMs = System.currentTimeMillis() - startTime;
            
            // Record metrics
            meterRegistry.timer("gateway.query.execution", "queryId", queryId)
                    .record(executionTimeMs, TimeUnit.MILLISECONDS);
            meterRegistry.counter("gateway.query.count", "queryId", queryId, "status", "success").increment();
            
            log.info("Query executed successfully: queryId={}, totalRows={}, executionTimeMs={}", 
                    queryId, totalRows, executionTimeMs);

            // Registrar auditoría en MySQL (gateway_query_execution)
            if (catalogRepository.isPresent()) {
                catalogRepository.get().recordExecution(com.gateway.catalog.GatewayQueryExecutionEntity.builder()
                        .queryCode(queryId)
                        .datasourceId(datasourceId)
                        .executedBy(executedBy)
                        .executionTimeMs(executionTimeMs)
                        .rowsReturned(rows.size())
                        .success(true)
                        .sourceIp(clientIp)
                        .build());
            }
            
            return QueryResult.builder()
                    .success(true)
                    .columns(columns)
                    .rows(rows)
                    .totalRows(totalRows)
                    .page(page)
                    .pageSize(pageSize)
                    .totalPages(totalPages)
                    .executionTimeMs(executionTimeMs)
                    .build();
                    
        } catch (Exception e) {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            meterRegistry.counter("gateway.query.count", "queryId", queryId, "status", "error").increment();
            log.error("Error executing query: queryId={}, error={}", queryId, e.getMessage(), e);

            if (catalogRepository.isPresent()) {
                catalogRepository.get().recordExecution(com.gateway.catalog.GatewayQueryExecutionEntity.builder()
                        .queryCode(queryId)
                        .datasourceId(datasourceId)
                        .executedBy(executedBy)
                        .executionTimeMs(executionTimeMs)
                        .rowsReturned(0)
                        .success(false)
                        .errorMessage(e.getMessage())
                        .sourceIp(clientIp)
                        .build());
            }

            return QueryResult.error("QUERY_EXECUTION_ERROR", e.getMessage());
        }
    }

    /**
     * Ejecuta una consulta administrativa libre controlada.
     * Valida reglas de solo lectura con SqlSanitizer y audita la llamada.
     */
    public QueryResult executeAdminQuery(com.gateway.dto.AdminQueryRequest request, String username, String clientIp) {
        long startTime = System.currentTimeMillis();
        String sql = request.getSql();

        log.info("ADMIN_AUDIT_REQUEST: user={}, ip={}, sql='{}'", username, clientIp, sql);

        // Validar que sea un SELECT seguro y no destructivo
        try {
            com.gateway.security.SqlSanitizer.validateReadOnlySelect(sql);
        } catch (Exception e) {
            log.warn("ADMIN_AUDIT_BLOCKED: user={}, ip={}, sql='{}', reason='{}'", username, clientIp, sql, e.getMessage());
            return QueryResult.error("SECURITY_VIOLATION", e.getMessage());
        }

        try {
            int maxRows = gatewayProperties.getAdminQuery().getMaxPageSize();
            int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ?
                    Math.min(request.getPageSize(), maxRows) :
                    gatewayProperties.getAdminQuery().getDefaultPageSize();

            List<Map<String, Object>> allResults = new ArrayList<>();
            List<ColumnInfo> columns = new ArrayList<>();

            jdbcTemplate.query(sql, rs -> {
                if (columns.isEmpty()) {
                    columns.addAll(extractColumns(rs.getMetaData()));
                }
                if (allResults.size() < maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        Object val = com.gateway.util.JdbcValueFormatter.formatValue(rs.getObject(i));
                        row.put(meta.getColumnLabel(i), val);
                    }
                    allResults.add(row);
                }
            });

            int totalRows = allResults.size();
            int totalPages = (int) Math.ceil((double) totalRows / pageSize);
            int fromIndex = Math.min((page - 1) * pageSize, totalRows);
            int toIndex = Math.min(fromIndex + pageSize, totalRows);
            List<Map<String, Object>> pagedResults = allResults.subList(fromIndex, toIndex);
            List<List<Object>> rows = buildRows(pagedResults, columns);

            long executionTimeMs = System.currentTimeMillis() - startTime;

            // Log estructurado de auditoría
            log.info("ADMIN_AUDIT_SUCCESS: user={}, ip={}, durationMs={}, rowsReturned={}, totalRows={}, sql='{}'",
                    username, clientIp, executionTimeMs, rows.size(), totalRows, sql);

            meterRegistry.timer("gateway.admin.query.execution").record(executionTimeMs, TimeUnit.MILLISECONDS);
            meterRegistry.counter("gateway.admin.query.count", "status", "success").increment();

            return QueryResult.builder()
                    .success(true)
                    .columns(columns)
                    .rows(rows)
                    .totalRows(totalRows)
                    .page(page)
                    .pageSize(pageSize)
                    .totalPages(totalPages)
                    .executionTimeMs(executionTimeMs)
                    .build();

        } catch (Exception e) {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            log.error("ADMIN_AUDIT_ERROR: user={}, ip={}, durationMs={}, sql='{}', error='{}'",
                    username, clientIp, executionTimeMs, sql, e.getMessage(), e);

            meterRegistry.counter("gateway.admin.query.count", "status", "error").increment();
            return QueryResult.error("SQL_EXECUTION_ERROR", e.getMessage());
        }
    }

    public WriteResult executeRegisteredOperation(String operationId, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        
        log.info("Executing registered operation: operationId={}, params={}", operationId, params);
        
        GatewayProperties.RegisteredQuery registeredOp = gatewayProperties.getRegisteredQueries().get(operationId);
        if (registeredOp == null) {
            return WriteResult.error("OPERATION_NOT_FOUND", "Operation not found: " + operationId);
        }
        
        String opType = registeredOp.getType().toUpperCase();
        if ("SELECT".equals(opType)) {
            return WriteResult.error("INVALID_OPERATION_TYPE", "Operation '" + operationId + "' is a SELECT, use query endpoint");
        }
        if ("DELETE".equals(opType) && !gatewayProperties.getOperations().isDeleteEnabled()) {
            return WriteResult.error("DELETE_DISABLED", "DELETE operations are disabled");
        }
        
        try {
            MapSqlParameterSource paramSource = new MapSqlParameterSource();
            if (params != null) {
                params.forEach(paramSource::addValue);
            }
            
            int rowsAffected = namedParameterJdbcTemplate.update(registeredOp.getSql(), paramSource);
            long executionTimeMs = System.currentTimeMillis() - startTime;
            
            meterRegistry.timer("gateway.operation.execution", "operationId", operationId, "type", opType)
                    .record(executionTimeMs, TimeUnit.MILLISECONDS);
            meterRegistry.counter("gateway.operation.count", "operationId", operationId, "type", opType, "status", "success").increment();
            
            if (gatewayProperties.getOperations().isAuditEnabled()) {
                auditOperation(operationId, params, rowsAffected, executionTimeMs);
            }
            
            log.info("Operation executed: operationId={}, rowsAffected={}, executionTimeMs={}", 
                    operationId, rowsAffected, executionTimeMs);
            
            return WriteResult.success(rowsAffected, executionTimeMs, operationId);
            
        } catch (Exception e) {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            meterRegistry.counter("gateway.operation.count", "operationId", operationId, "status", "error").increment();
            log.error("Error executing operation: operationId={}, error={}", operationId, e.getMessage(), e);
            return WriteResult.error("OPERATION_EXECUTION_ERROR", e.getMessage());
        }
    }

    public TransactionResult executeTransaction(TransactionRequest request) {
        long startTime = System.currentTimeMillis();
        List<WriteResult> results = new ArrayList<>();
        Connection connection = null;
        
        log.info("Starting transaction with {} operations", request.getOperations().size());
        
        try {
            connection = DataSourceUtils.getConnection(dataSource);
            connection.setAutoCommit(false);
            
            for (int i = 0; i < request.getOperations().size(); i++) {
                TransactionRequest.TransactionOperation op = request.getOperations().get(i);
                long opStart = System.currentTimeMillis();
                
                GatewayProperties.RegisteredQuery registeredQuery = 
                        gatewayProperties.getRegisteredQueries().get(op.getQueryId());
                        
                if (registeredQuery == null) {
                    connection.rollback();
                    return TransactionResult.builder()
                            .success(false)
                            .operationResults(results)
                            .failedOperationIndex(i)
                            .failedOperationId(op.getQueryId())
                            .errorCode("OPERATION_NOT_FOUND")
                            .message("Operation not found: " + op.getQueryId())
                            .totalExecutionTimeMs(System.currentTimeMillis() - startTime)
                            .build();
                }
                
                MapSqlParameterSource paramSource = new MapSqlParameterSource();
                if (op.getParams() != null) {
                    op.getParams().forEach(paramSource::addValue);
                }
                
                try {
                    int rowsAffected = namedParameterJdbcTemplate.update(registeredQuery.getSql(), paramSource);
                    long opTime = System.currentTimeMillis() - opStart;
                    results.add(WriteResult.success(rowsAffected, opTime, op.getQueryId()));
                    log.debug("Transaction operation {} completed: queryId={}, rowsAffected={}", i, op.getQueryId(), rowsAffected);
                } catch (Exception opEx) {
                    connection.rollback();
                    log.error("Transaction rolled back at operation {}: {}", i, opEx.getMessage());
                    return TransactionResult.builder()
                            .success(false)
                            .operationResults(results)
                            .failedOperationIndex(i)
                            .failedOperationId(op.getQueryId())
                            .errorCode("TRANSACTION_OPERATION_FAILED")
                            .message(opEx.getMessage())
                            .totalExecutionTimeMs(System.currentTimeMillis() - startTime)
                            .build();
                }
            }
            
            connection.commit();
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Transaction committed successfully: {} operations, totalTimeMs={}", results.size(), totalTime);
            
            return TransactionResult.builder()
                    .success(true)
                    .operationResults(results)
                    .totalExecutionTimeMs(totalTime)
                    .build();
                    
        } catch (SQLException e) {
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException re) {
                log.error("Rollback failed", re);
            }
            log.error("Transaction failed: {}", e.getMessage(), e);
            return TransactionResult.builder()
                    .success(false)
                    .operationResults(results)
                    .errorCode("TRANSACTION_ERROR")
                    .message(e.getMessage())
                    .totalExecutionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        }
    }

    private void auditOperation(String operationId, Map<String, Object> params, int rowsAffected, long executionTimeMs) {
        log.info("AUDIT: {{\"operationId\":\"{}\",\"rowsAffected\":{},\"executionTimeMs\":{},\"params\":{}}}",
                operationId, rowsAffected, executionTimeMs, params);
    }

    private List<ColumnInfo> extractColumns(ResultSetMetaData meta) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(ColumnInfo.builder()
                    .name(meta.getColumnName(i))
                    .label(meta.getColumnLabel(i))
                    .type(meta.getColumnTypeName(i))
                    .jdbcType(meta.getColumnType(i))
                    .columnSize(meta.getColumnDisplaySize(i))
                    .nullable(meta.isNullable(i) != ResultSetMetaData.columnNoNulls)
                    .build());
        }
        return columns;
    }

    private List<List<Object>> buildRows(List<Map<String, Object>> results, List<ColumnInfo> columns) {
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> rowMap : results) {
            List<Object> row = new ArrayList<>();
            for (ColumnInfo col : columns) {
                row.add(rowMap.get(col.getLabel() != null ? col.getLabel() : col.getName()));
            }
            rows.add(row);
        }
        return rows;
    }
}
