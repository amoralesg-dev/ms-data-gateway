package com.gateway.service;

import com.gateway.config.GatewayProperties;
import com.gateway.dto.QueryRequest;
import com.gateway.model.QueryResult;
import com.gateway.model.WriteResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataGatewayServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private DataSource dataSource;

    private MeterRegistry meterRegistry;
    private GatewayProperties gatewayProperties;
    private DataGatewayService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        gatewayProperties = new GatewayProperties();

        GatewayProperties.Queries queries = new GatewayProperties.Queries();
        queries.setMaxRows(1000);
        queries.setDefaultPageSize(50);
        gatewayProperties.setQueries(queries);

        GatewayProperties.Operations operations = new GatewayProperties.Operations();
        operations.setDeleteEnabled(true);
        operations.setAuditEnabled(false);
        gatewayProperties.setOperations(operations);

        Map<String, GatewayProperties.RegisteredQuery> registeredQueries = new HashMap<>();

        GatewayProperties.RegisteredQuery selectQuery = new GatewayProperties.RegisteredQuery();
        selectQuery.setSql("SELECT * FROM pub.addr WHERE ad_addr LIKE :prefix");
        selectQuery.setDescription("Test SELECT query");
        selectQuery.setType("SELECT");
        registeredQueries.put("test-select", selectQuery);

        GatewayProperties.RegisteredQuery updateOp = new GatewayProperties.RegisteredQuery();
        updateOp.setSql("UPDATE pub.addr SET ad_name = :name WHERE ad_addr = :code");
        updateOp.setDescription("Test UPDATE operation");
        updateOp.setType("UPDATE");
        registeredQueries.put("test-update", updateOp);

        GatewayProperties.RegisteredQuery deleteOp = new GatewayProperties.RegisteredQuery();
        deleteOp.setSql("DELETE FROM pub.addr WHERE ad_addr = :code");
        deleteOp.setDescription("Test DELETE operation");
        deleteOp.setType("DELETE");
        registeredQueries.put("test-delete", deleteOp);

        gatewayProperties.setRegisteredQueries(registeredQueries);

        service = new DataGatewayService(jdbcTemplate, namedParameterJdbcTemplate,
                gatewayProperties, meterRegistry, dataSource);
    }

    // ==================== SELECT Tests ====================

    @Test
    @DisplayName("Debe retornar error cuando el queryId no existe")
    void executeRegisteredQuery_queryNotFound() {
        QueryRequest request = new QueryRequest();
        request.setQueryId("query-inexistente");

        QueryResult result = service.executeRegisteredQuery(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("QUERY_NOT_FOUND");
        assertThat(result.getMessage()).contains("query-inexistente");
    }

    @Test
    @DisplayName("Debe retornar error cuando el tipo de query no es SELECT")
    void executeRegisteredQuery_invalidType_update() {
        QueryRequest request = new QueryRequest();
        request.setQueryId("test-update"); // Es UPDATE, no SELECT

        QueryResult result = service.executeRegisteredQuery(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_QUERY_TYPE");
    }

    @Test
    @DisplayName("Debe retornar error cuando la ejecucion de la query falla")
    void executeRegisteredQuery_executionError() {
        QueryRequest request = new QueryRequest();
        request.setQueryId("test-select");

        doThrow(new RuntimeException("Connection refused - OpenEdge not available"))
                .when(namedParameterJdbcTemplate)
                .query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));

        QueryResult result = service.executeRegisteredQuery(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("QUERY_EXECUTION_ERROR");
    }

    @Test
    @DisplayName("Debe retornar resultado exitoso con lista vacia cuando no hay datos")
    void executeRegisteredQuery_emptyResult() {
        QueryRequest request = new QueryRequest();
        request.setQueryId("test-select");
        request.setParams(Map.of("prefix", "ZZZNONE%"));

        // No mockear el query - simula 0 filas (callback nunca es llamado)
        doNothing().when(namedParameterJdbcTemplate)
                .query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));

        QueryResult result = service.executeRegisteredQuery(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalRows()).isEqualTo(0);
        assertThat(result.getRows()).isEmpty();
    }

    // ==================== Operation Tests ====================

    @Test
    @DisplayName("Debe retornar error cuando el operationId no existe")
    void executeRegisteredOperation_notFound() {
        WriteResult result = service.executeRegisteredOperation("operacion-inexistente", new HashMap<>());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("OPERATION_NOT_FOUND");
    }

    @Test
    @DisplayName("Debe retornar error al intentar ejecutar SELECT como operacion de escritura")
    void executeRegisteredOperation_invalidType_select() {
        WriteResult result = service.executeRegisteredOperation("test-select", new HashMap<>());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_OPERATION_TYPE");
    }

    @Test
    @DisplayName("Debe retornar error cuando DELETE esta deshabilitado")
    void executeRegisteredOperation_deleteDisabled() {
        gatewayProperties.getOperations().setDeleteEnabled(false);

        WriteResult result = service.executeRegisteredOperation("test-delete", new HashMap<>());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("DELETE_DISABLED");
    }

    @Test
    @DisplayName("Debe ejecutar operacion UPDATE exitosamente")
    void executeRegisteredOperation_updateSuccess() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Proveedor Actualizado");
        params.put("code", "000001");

        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        WriteResult result = service.executeRegisteredOperation("test-update", params);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowsAffected()).isEqualTo(1);
        assertThat(result.getOperationId()).isEqualTo("test-update");
        assertThat(result.getExecutionTimeMs()).isNotNull();
    }

    @Test
    @DisplayName("Debe ejecutar operacion DELETE exitosamente cuando esta habilitado")
    void executeRegisteredOperation_deleteSuccess() {
        Map<String, Object> params = Map.of("code", "TEST01");

        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        WriteResult result = service.executeRegisteredOperation("test-delete", params);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowsAffected()).isEqualTo(1);
    }

    @Test
    @DisplayName("Debe retornar error cuando la operacion falla en la BD")
    void executeRegisteredOperation_dbError() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new RuntimeException("Duplicate key violation"));

        WriteResult result = service.executeRegisteredOperation("test-update", Map.of("name", "X", "code", "Y"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("OPERATION_EXECUTION_ERROR");
    }

    @Test
    @DisplayName("Debe operar con params nulos sin NPE")
    void executeRegisteredOperation_nullParams() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(0);

        WriteResult result = service.executeRegisteredOperation("test-update", null);

        // No debe lanzar NullPointerException
        assertThat(result).isNotNull();
    }

    // ==================== Metrics Tests ====================

    @Test
    @DisplayName("Debe registrar metricas de contador en ejecucion exitosa")
    void executeRegisteredOperation_recordsMetrics() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        service.executeRegisteredOperation("test-update", Map.of("name", "X", "code", "Y"));

        // Verificar que la metrica fue registrada
        assertThat(meterRegistry.find("gateway.operation.count")
                .tag("status", "success")
                .counter()).isNotNull();
    }
}
