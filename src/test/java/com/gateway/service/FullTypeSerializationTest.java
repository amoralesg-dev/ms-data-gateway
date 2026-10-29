package com.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.config.GatewayProperties;
import com.gateway.dto.QueryRequest;
import com.gateway.model.ColumnInfo;
import com.gateway.model.QueryResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FullTypeSerializationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private DataSource dataSource;

    private DataGatewayService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.RegisteredQuery query = new GatewayProperties.RegisteredQuery();
        query.setType("SELECT");
        query.setSql("SELECT * FROM pub.sample_types");
        Map<String, GatewayProperties.RegisteredQuery> map = new HashMap<>();
        map.put("all-types", query);
        properties.setRegisteredQueries(map);

        service = new DataGatewayService(
                jdbcTemplate,
                namedParameterJdbcTemplate,
                properties,
                new SimpleMeterRegistry(),
                dataSource
        );
    }

    @Test
    @DisplayName("Valida serialización real a JSON de VARCHAR, INTEGER, DECIMAL, DATE, TIME, TIMESTAMP")
    void testAllTypesJsonSerialization() throws Exception {
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(meta.getColumnCount()).thenReturn(6);

        // 1. VARCHAR
        when(meta.getColumnName(1)).thenReturn("col_varchar");
        when(meta.getColumnLabel(1)).thenReturn("col_varchar");
        when(meta.getColumnTypeName(1)).thenReturn("VARCHAR");
        when(meta.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(meta.getColumnDisplaySize(1)).thenReturn(50);
        when(meta.isNullable(1)).thenReturn(ResultSetMetaData.columnNullable);

        // 2. INTEGER
        when(meta.getColumnName(2)).thenReturn("col_integer");
        when(meta.getColumnLabel(2)).thenReturn("col_integer");
        when(meta.getColumnTypeName(2)).thenReturn("INTEGER");
        when(meta.getColumnType(2)).thenReturn(Types.INTEGER);
        when(meta.getColumnDisplaySize(2)).thenReturn(10);
        when(meta.isNullable(2)).thenReturn(ResultSetMetaData.columnNoNulls);

        // 3. DECIMAL
        when(meta.getColumnName(3)).thenReturn("col_decimal");
        when(meta.getColumnLabel(3)).thenReturn("col_decimal");
        when(meta.getColumnTypeName(3)).thenReturn("DECIMAL");
        when(meta.getColumnType(3)).thenReturn(Types.DECIMAL);
        when(meta.getColumnDisplaySize(3)).thenReturn(18);
        when(meta.isNullable(3)).thenReturn(ResultSetMetaData.columnNullable);

        // 4. DATE
        when(meta.getColumnName(4)).thenReturn("col_date");
        when(meta.getColumnLabel(4)).thenReturn("col_date");
        when(meta.getColumnTypeName(4)).thenReturn("DATE");
        when(meta.getColumnType(4)).thenReturn(Types.DATE);
        when(meta.getColumnDisplaySize(4)).thenReturn(10);
        when(meta.isNullable(4)).thenReturn(ResultSetMetaData.columnNullable);

        // 5. TIME
        when(meta.getColumnName(5)).thenReturn("col_time");
        when(meta.getColumnLabel(5)).thenReturn("col_time");
        when(meta.getColumnTypeName(5)).thenReturn("TIME");
        when(meta.getColumnType(5)).thenReturn(Types.TIME);
        when(meta.getColumnDisplaySize(5)).thenReturn(8);
        when(meta.isNullable(5)).thenReturn(ResultSetMetaData.columnNullable);

        // 6. TIMESTAMP
        when(meta.getColumnName(6)).thenReturn("col_timestamp");
        when(meta.getColumnLabel(6)).thenReturn("col_timestamp");
        when(meta.getColumnTypeName(6)).thenReturn("TIMESTAMP");
        when(meta.getColumnType(6)).thenReturn(Types.TIMESTAMP);
        when(meta.getColumnDisplaySize(6)).thenReturn(26);
        when(meta.isNullable(6)).thenReturn(ResultSetMetaData.columnNullable);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getMetaData()).thenReturn(meta);
        when(rs.getObject(1)).thenReturn("Texto de prueba OpenEdge");
        when(rs.getObject(2)).thenReturn(98765);
        when(rs.getObject(3)).thenReturn(new BigDecimal("123456.789"));
        when(rs.getObject(4)).thenReturn(Date.valueOf("2026-09-03"));
        when(rs.getObject(5)).thenReturn(Time.valueOf("14:30:15"));
        when(rs.getObject(6)).thenReturn(Timestamp.valueOf("2026-09-03 14:30:15.123"));

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(rs);
            return null;
        }).when(namedParameterJdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), any(RowCallbackHandler.class));

        QueryRequest request = new QueryRequest();
        request.setQueryId("all-types");

        QueryResult result = service.executeRegisteredQuery(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getColumns()).hasSize(6);

        // Serializar a JSON real con Jackson
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println("JSON SERIALIZADO COMPLETO:\n" + json);

        // Verificaciones sobre el JSON resultante
        assertThat(json).contains("\"name\" : \"col_varchar\"");
        assertThat(json).contains("\"name\" : \"col_integer\"");
        assertThat(json).contains("\"name\" : \"col_decimal\"");
        assertThat(json).contains("\"name\" : \"col_date\"");
        assertThat(json).contains("\"name\" : \"col_time\"");
        assertThat(json).contains("\"name\" : \"col_timestamp\"");

        // Valores serializados reales con JdbcValueFormatter (ISO-8601)
        assertThat(json).contains("\"Texto de prueba OpenEdge\"");
        assertThat(json).contains("98765");
        assertThat(json).contains("123456.789");
        assertThat(json).contains("\"2026-09-03\"");
        assertThat(json).contains("\"14:30:15\"");
        assertThat(json).contains("2026-09-03T");
    }
}
