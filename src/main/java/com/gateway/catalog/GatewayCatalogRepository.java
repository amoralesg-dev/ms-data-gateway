package com.gateway.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JDBC para gestionar el catálogo persistente en MySQL.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GatewayCatalogRepository {

    private final JdbcTemplate jdbcTemplate;

    // ─────────────────────────────────────────────────────────────────────────
    // Datasources
    // ─────────────────────────────────────────────────────────────────────────
    public List<GatewayDatasourceEntity> findAllDatasources() {
        String sql = "SELECT datasource_id, datasource_code, datasource_name, engine_type, enabled, default_flag, description, created_date, updated_date FROM gateway_datasource ORDER BY datasource_id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapDatasource(rs));
    }

    public Optional<GatewayDatasourceEntity> findDatasourceById(String datasourceId) {
        String sql = "SELECT datasource_id, datasource_code, datasource_name, engine_type, enabled, default_flag, description, created_date, updated_date FROM gateway_datasource WHERE datasource_id = ?";
        List<GatewayDatasourceEntity> list = jdbcTemplate.query(sql, (rs, rowNum) -> mapDatasource(rs), datasourceId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<GatewayDatasourceEntity> findDatasourceByCode(String datasourceCode) {
        String sql = "SELECT datasource_id, datasource_code, datasource_name, engine_type, enabled, default_flag, description, created_date, updated_date FROM gateway_datasource WHERE datasource_code = ?";
        List<GatewayDatasourceEntity> list = jdbcTemplate.query(sql, (rs, rowNum) -> mapDatasource(rs), datasourceCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<GatewayDatasourceEntity> findDefaultDatasource() {
        String sql = "SELECT datasource_id, datasource_code, datasource_name, engine_type, enabled, default_flag, description, created_date, updated_date FROM gateway_datasource WHERE default_flag = 1 LIMIT 1";
        List<GatewayDatasourceEntity> list = jdbcTemplate.query(sql, (rs, rowNum) -> mapDatasource(rs));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void clearDefaultFlag() {
        jdbcTemplate.update("UPDATE gateway_datasource SET default_flag = 0");
    }

    public void insertDatasource(GatewayDatasourceEntity ds) {
        String sql = "INSERT INTO gateway_datasource (datasource_id, datasource_code, datasource_name, engine_type, enabled, default_flag, description) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                ds.getDatasourceId(),
                ds.getDatasourceCode(),
                ds.getDatasourceName(),
                ds.getEngineType(),
                ds.isEnabled() ? 1 : 0,
                ds.isDefaultFlag() ? 1 : 0,
                ds.getDescription()
        );
    }

    public void updateDatasource(String datasourceId, GatewayDatasourceEntity ds) {
        String sql = "UPDATE gateway_datasource SET datasource_code = ?, datasource_name = ?, engine_type = ?, enabled = ?, default_flag = ?, description = ? WHERE datasource_id = ?";
        jdbcTemplate.update(sql,
                ds.getDatasourceCode(),
                ds.getDatasourceName(),
                ds.getEngineType(),
                ds.isEnabled() ? 1 : 0,
                ds.isDefaultFlag() ? 1 : 0,
                ds.getDescription(),
                datasourceId
        );
    }

    public int countQueriesByDatasourceId(String datasourceId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gateway_query WHERE datasource_id = ?", Integer.class, datasourceId);
        return count != null ? count : 0;
    }

    public void deleteDatasource(String datasourceId) {
        jdbcTemplate.update("DELETE FROM gateway_datasource WHERE datasource_id = ?", datasourceId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────
    public int countQueries() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gateway_query", Integer.class);
        return count != null ? count : 0;
    }

    public List<GatewayQueryEntity> findAllQueries() {
        String sql = "SELECT query_code, query_name, datasource_id, query_type, sql_text, enabled, timeout_seconds, max_rows, description, created_by, created_date, updated_by, updated_date FROM gateway_query ORDER BY query_code";
        return jdbcTemplate.query(sql, new QueryRowMapper());
    }

    public Optional<GatewayQueryEntity> findQueryByCode(String queryCode) {
        String sql = "SELECT query_code, query_name, datasource_id, query_type, sql_text, enabled, timeout_seconds, max_rows, description, created_by, created_date, updated_by, updated_date FROM gateway_query WHERE query_code = ?";
        List<GatewayQueryEntity> list = jdbcTemplate.query(sql, new QueryRowMapper(), queryCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void insertQuery(GatewayQueryEntity q) {
        String sql = "INSERT INTO gateway_query (query_code, query_name, datasource_id, query_type, sql_text, enabled, timeout_seconds, max_rows, description, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                q.getQueryCode(),
                q.getQueryName(),
                q.getDatasourceId(),
                q.getQueryType(),
                q.getSqlText(),
                q.isEnabled() ? 1 : 0,
                q.getTimeoutSeconds(),
                q.getMaxRows(),
                q.getDescription(),
                q.getCreatedBy() != null ? q.getCreatedBy() : "SYSTEM"
        );
    }

    public void updateQuery(String queryCode, GatewayQueryEntity q, String updatedBy) {
        String sql = "UPDATE gateway_query SET query_name = ?, datasource_id = ?, query_type = ?, sql_text = ?, enabled = ?, timeout_seconds = ?, max_rows = ?, description = ?, updated_by = ? WHERE query_code = ?";
        jdbcTemplate.update(sql,
                q.getQueryName(),
                q.getDatasourceId(),
                q.getQueryType(),
                q.getSqlText(),
                q.isEnabled() ? 1 : 0,
                q.getTimeoutSeconds(),
                q.getMaxRows(),
                q.getDescription(),
                updatedBy != null ? updatedBy : "ADMIN",
                queryCode
        );
    }

    public void toggleQueryEnabled(String queryCode, boolean enabled, String updatedBy) {
        String sql = "UPDATE gateway_query SET enabled = ?, updated_by = ? WHERE query_code = ?";
        jdbcTemplate.update(sql, enabled ? 1 : 0, updatedBy != null ? updatedBy : "ADMIN", queryCode);
    }

    public void deleteQuery(String queryCode) {
        jdbcTemplate.update("DELETE FROM gateway_query WHERE query_code = ?", queryCode);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Query Parameters
    // ─────────────────────────────────────────────────────────────────────────
    public List<GatewayQueryParameterEntity> findParametersByQueryCode(String queryCode) {
        String sql = "SELECT id, query_code, parameter_name, parameter_type, required_flag, default_value FROM gateway_query_parameter WHERE query_code = ? ORDER BY id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> GatewayQueryParameterEntity.builder()
                .id(rs.getLong("id"))
                .queryCode(rs.getString("query_code"))
                .parameterName(rs.getString("parameter_name"))
                .parameterType(rs.getString("parameter_type"))
                .requiredFlag(rs.getBoolean("required_flag"))
                .defaultValue(rs.getString("default_value"))
                .build(), queryCode);
    }

    public Optional<GatewayQueryParameterEntity> findParameterById(Long id) {
        String sql = "SELECT id, query_code, parameter_name, parameter_type, required_flag, default_value FROM gateway_query_parameter WHERE id = ?";
        List<GatewayQueryParameterEntity> list = jdbcTemplate.query(sql, (rs, rowNum) -> GatewayQueryParameterEntity.builder()
                .id(rs.getLong("id"))
                .queryCode(rs.getString("query_code"))
                .parameterName(rs.getString("parameter_name"))
                .parameterType(rs.getString("parameter_type"))
                .requiredFlag(rs.getBoolean("required_flag"))
                .defaultValue(rs.getString("default_value"))
                .build(), id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void insertParameter(GatewayQueryParameterEntity p) {
        String sql = "INSERT INTO gateway_query_parameter (query_code, parameter_name, parameter_type, required_flag, default_value) " +
                     "VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                p.getQueryCode(),
                p.getParameterName(),
                p.getParameterType(),
                p.isRequiredFlag() ? 1 : 0,
                p.getDefaultValue()
        );
    }

    public void updateParameter(Long id, GatewayQueryParameterEntity p) {
        String sql = "UPDATE gateway_query_parameter SET parameter_name = ?, parameter_type = ?, required_flag = ?, default_value = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                p.getParameterName(),
                p.getParameterType(),
                p.isRequiredFlag() ? 1 : 0,
                p.getDefaultValue(),
                id
        );
    }

    public Optional<GatewayQueryParameterEntity> findParameterByName(String queryCode, String parameterName) {
        String sql = "SELECT id, query_code, parameter_name, parameter_type, required_flag, default_value FROM gateway_query_parameter WHERE query_code = ? AND parameter_name = ?";
        List<GatewayQueryParameterEntity> list = jdbcTemplate.query(sql, (rs, rowNum) -> GatewayQueryParameterEntity.builder()
                .id(rs.getLong("id"))
                .queryCode(rs.getString("query_code"))
                .parameterName(rs.getString("parameter_name"))
                .parameterType(rs.getString("parameter_type"))
                .requiredFlag(rs.getBoolean("required_flag"))
                .defaultValue(rs.getString("default_value"))
                .build(), queryCode, parameterName);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void updateParameterByName(String queryCode, String parameterName, GatewayQueryParameterEntity p) {
        String sql = "UPDATE gateway_query_parameter SET parameter_type = ?, required_flag = ?, default_value = ? WHERE query_code = ? AND parameter_name = ?";
        jdbcTemplate.update(sql,
                p.getParameterType(),
                p.isRequiredFlag() ? 1 : 0,
                p.getDefaultValue(),
                queryCode,
                parameterName
        );
    }

    public void deleteParameterByName(String queryCode, String parameterName) {
        jdbcTemplate.update("DELETE FROM gateway_query_parameter WHERE query_code = ? AND parameter_name = ?", queryCode, parameterName);
    }

    public void deleteParameter(Long id) {
        jdbcTemplate.update("DELETE FROM gateway_query_parameter WHERE id = ?", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Query Execution Audit
    // ─────────────────────────────────────────────────────────────────────────
    public void recordExecution(GatewayQueryExecutionEntity e) {
        String sql = "INSERT INTO gateway_query_execution (query_code, datasource_id, executed_by, execution_time_ms, rows_returned, success, error_message, source_ip) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql,
                    e.getQueryCode(),
                    e.getDatasourceId(),
                    e.getExecutedBy(),
                    e.getExecutionTimeMs(),
                    e.getRowsReturned(),
                    e.isSuccess() ? 1 : 0,
                    e.getErrorMessage(),
                    e.getSourceIp()
            );
        } catch (Exception ex) {
            log.error("No fue posible registrar la ejecución en gateway_query_execution: {}", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Business Services (QAD SOAP / QXtend)
    // ─────────────────────────────────────────────────────────────────────────
    public List<GatewayServiceEntity> findAllServices() {
        String sql = "SELECT id, service_code, service_name, service_type, mapper_class, operation_name, endpoint_url, enabled, timeout_seconds, created_date, updated_date FROM gateway_service ORDER BY service_code";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapService(rs));
    }

    public Optional<GatewayServiceEntity> findServiceByCode(String serviceCode) {
        String sql = "SELECT id, service_code, service_name, service_type, mapper_class, operation_name, endpoint_url, enabled, timeout_seconds, created_date, updated_date FROM gateway_service WHERE service_code = ?";
        List<GatewayServiceEntity> list = jdbcTemplate.query(sql, (rs, rowNum) -> mapService(rs), serviceCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void insertService(GatewayServiceEntity s) {
        String sql = "INSERT INTO gateway_service (service_code, service_name, service_type, mapper_class, operation_name, endpoint_url, enabled, timeout_seconds) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                s.getServiceCode(),
                s.getServiceName(),
                s.getServiceType() != null ? s.getServiceType() : "SOAP_QXtend",
                s.getMapperClass(),
                s.getOperationName(),
                s.getEndpointUrl(),
                s.isEnabled() ? 1 : 0,
                s.getTimeoutSeconds() > 0 ? s.getTimeoutSeconds() : 60
        );
    }

    public void updateService(String serviceCode, GatewayServiceEntity s) {
        String sql = "UPDATE gateway_service SET service_name = ?, service_type = ?, mapper_class = ?, operation_name = ?, endpoint_url = ?, enabled = ?, timeout_seconds = ? WHERE service_code = ?";
        jdbcTemplate.update(sql,
                s.getServiceName(),
                s.getServiceType(),
                s.getMapperClass(),
                s.getOperationName(),
                s.getEndpointUrl(),
                s.isEnabled() ? 1 : 0,
                s.getTimeoutSeconds(),
                serviceCode
        );
    }

    public void deleteService(String serviceCode) {
        jdbcTemplate.update("DELETE FROM gateway_service WHERE service_code = ?", serviceCode);
    }

    public void recordServiceExecution(GatewayServiceExecutionEntity e) {
        String sql = "INSERT INTO gateway_service_execution (service_code, request_payload, response_payload, status, error_code, error_message, execution_time_ms, executed_by, source_ip) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql,
                    e.getServiceCode(),
                    e.getRequestPayload(),
                    e.getResponsePayload(),
                    e.getStatus(),
                    e.getErrorCode(),
                    e.getErrorMessage(),
                    e.getExecutionTimeMs(),
                    e.getExecutedBy(),
                    e.getSourceIp()
            );
        } catch (Exception ex) {
            log.error("No fue posible registrar la ejecución en gateway_service_execution: {}", ex.getMessage());
        }
    }

    private static GatewayServiceEntity mapService(ResultSet rs) throws SQLException {
        return GatewayServiceEntity.builder()
                .id(rs.getLong("id"))
                .serviceCode(rs.getString("service_code"))
                .serviceName(rs.getString("service_name"))
                .serviceType(rs.getString("service_type"))
                .mapperClass(rs.getString("mapper_class"))
                .operationName(rs.getString("operation_name"))
                .endpointUrl(rs.getString("endpoint_url"))
                .enabled(rs.getBoolean("enabled"))
                .timeoutSeconds(rs.getInt("timeout_seconds"))
                .createdDate(getLocalDateTime(rs, "created_date"))
                .updatedDate(getLocalDateTime(rs, "updated_date"))
                .build();
    }

    private static GatewayDatasourceEntity mapDatasource(ResultSet rs) throws SQLException {
        return GatewayDatasourceEntity.builder()
                .datasourceId(rs.getString("datasource_id"))
                .datasourceCode(rs.getString("datasource_code"))
                .datasourceName(rs.getString("datasource_name"))
                .engineType(rs.getString("engine_type"))
                .enabled(rs.getBoolean("enabled"))
                .defaultFlag(rs.getBoolean("default_flag"))
                .description(rs.getString("description"))
                .createdDate(getLocalDateTime(rs, "created_date"))
                .updatedDate(getLocalDateTime(rs, "updated_date"))
                .build();
    }

    private static LocalDateTime getLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toLocalDateTime() : null;
    }

    private static class QueryRowMapper implements RowMapper<GatewayQueryEntity> {
        @Override
        public GatewayQueryEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            return GatewayQueryEntity.builder()
                    .queryCode(rs.getString("query_code"))
                    .queryName(rs.getString("query_name"))
                    .datasourceId(rs.getString("datasource_id"))
                    .queryType(rs.getString("query_type"))
                    .sqlText(rs.getString("sql_text"))
                    .enabled(rs.getBoolean("enabled"))
                    .timeoutSeconds(rs.getInt("timeout_seconds"))
                    .maxRows(rs.getInt("max_rows"))
                    .description(rs.getString("description"))
                    .createdBy(rs.getString("created_by"))
                    .createdDate(getLocalDateTime(rs, "created_date"))
                    .updatedBy(rs.getString("updated_by"))
                    .updatedDate(getLocalDateTime(rs, "updated_date"))
                    .build();
        }
    }
}
