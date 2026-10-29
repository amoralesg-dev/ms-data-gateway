package com.gateway.service;

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
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SerializationAndPaginationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private DataSource dataSource;

    private DataGatewayService service;
    private GatewayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        properties.getQueries().setMaxRows(100);
        properties.getQueries().setDefaultPageSize(2);

        GatewayProperties.RegisteredQuery query = new GatewayProperties.RegisteredQuery();
        query.setType("SELECT");
        query.setSql("SELECT * FROM pub.customer");
        query.setDescription("Customer listing");

        Map<String, GatewayProperties.RegisteredQuery> queryMap = new HashMap<>();
        queryMap.put("get-customers", query);
        properties.setRegisteredQueries(queryMap);

        service = new DataGatewayService(
                jdbcTemplate,
                namedParameterJdbcTemplate,
                properties,
                new SimpleMeterRegistry(),
                dataSource
        );
    }

    @Test
    @DisplayName("Debe serializar metadatos y convertir tipos JDBC correctamente")
    void testMetadataAndTypeConversion() throws SQLException {
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(meta.getColumnCount()).thenReturn(3);

        when(meta.getColumnName(1)).thenReturn("cust_id");
        when(meta.getColumnLabel(1)).thenReturn("cust_id");
        when(meta.getColumnTypeName(1)).thenReturn("INTEGER");
        when(meta.getColumnType(1)).thenReturn(Types.INTEGER);
        when(meta.getColumnDisplaySize(1)).thenReturn(10);
        when(meta.isNullable(1)).thenReturn(ResultSetMetaData.columnNoNulls);

        when(meta.getColumnName(2)).thenReturn("cust_name");
        when(meta.getColumnLabel(2)).thenReturn("cust_name");
        when(meta.getColumnTypeName(2)).thenReturn("VARCHAR");
        when(meta.getColumnType(2)).thenReturn(Types.VARCHAR);
        when(meta.getColumnDisplaySize(2)).thenReturn(50);
        when(meta.isNullable(2)).thenReturn(ResultSetMetaData.columnNullable);

        when(meta.getColumnName(3)).thenReturn("balance");
        when(meta.getColumnLabel(3)).thenReturn("balance");
        when(meta.getColumnTypeName(3)).thenReturn("DECIMAL");
        when(meta.getColumnType(3)).thenReturn(Types.DECIMAL);
        when(meta.getColumnDisplaySize(3)).thenReturn(15);
        when(meta.isNullable(3)).thenReturn(ResultSetMetaData.columnNullable);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getMetaData()).thenReturn(meta);
        when(rs.getObject(1)).thenReturn(1001);
        when(rs.getObject(2)).thenReturn("Acme Corp");
        when(rs.getObject(3)).thenReturn(45000.50);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(rs);
            return null;
        }).when(namedParameterJdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), any(RowCallbackHandler.class));

        QueryRequest request = new QueryRequest();
        request.setQueryId("get-customers");

        QueryResult result = service.executeRegisteredQuery(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getColumns()).hasSize(3);

        ColumnInfo col1 = result.getColumns().get(0);
        assertThat(col1.getName()).isEqualTo("cust_id");
        assertThat(col1.getType()).isEqualTo("INTEGER");
        assertThat(col1.getJdbcType()).isEqualTo(Types.INTEGER);
        assertThat(col1.isNullable()).isFalse();

        ColumnInfo col2 = result.getColumns().get(1);
        assertThat(col2.getName()).isEqualTo("cust_name");
        assertThat(col2.getType()).isEqualTo("VARCHAR");
        assertThat(col2.getJdbcType()).isEqualTo(Types.VARCHAR);
        assertThat(col2.isNullable()).isTrue();

        assertThat(result.getRows()).hasSize(1);
        List<Object> row = result.getRows().get(0);
        assertThat(row.get(0)).isEqualTo(1001);
        assertThat(row.get(1)).isEqualTo("Acme Corp");
        assertThat(row.get(2)).isEqualTo(45000.50);
    }

    @Test
    @DisplayName("Debe paginar filas correctamente en memoria")
    void testPaginationSlicing() throws SQLException {
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnName(1)).thenReturn("id");
        when(meta.getColumnLabel(1)).thenReturn("id");
        when(meta.getColumnTypeName(1)).thenReturn("INTEGER");

        ResultSet rs1 = mock(ResultSet.class);
        when(rs1.getMetaData()).thenReturn(meta);
        when(rs1.getObject(1)).thenReturn(1);

        ResultSet rs2 = mock(ResultSet.class);
        when(rs2.getMetaData()).thenReturn(meta);
        when(rs2.getObject(1)).thenReturn(2);

        ResultSet rs3 = mock(ResultSet.class);
        when(rs3.getMetaData()).thenReturn(meta);
        when(rs3.getObject(1)).thenReturn(3);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(rs1);
            handler.processRow(rs2);
            handler.processRow(rs3);
            return null;
        }).when(namedParameterJdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), any(RowCallbackHandler.class));

        QueryRequest page1Req = new QueryRequest();
        page1Req.setQueryId("get-customers");
        page1Req.setPage(1);
        page1Req.setPageSize(2);

        QueryResult page1Result = service.executeRegisteredQuery(page1Req);
        assertThat(page1Result.getTotalRows()).isEqualTo(3);
        assertThat(page1Result.getTotalPages()).isEqualTo(2);
        assertThat(page1Result.getRows()).hasSize(2);
        assertThat(page1Result.getRows().get(0).get(0)).isEqualTo(1);
        assertThat(page1Result.getRows().get(1).get(0)).isEqualTo(2);

        QueryRequest page2Req = new QueryRequest();
        page2Req.setQueryId("get-customers");
        page2Req.setPage(2);
        page2Req.setPageSize(2);

        QueryResult page2Result = service.executeRegisteredQuery(page2Req);
        assertThat(page2Result.getTotalRows()).isEqualTo(3);
        assertThat(page2Result.getRows()).hasSize(1);
        assertThat(page2Result.getRows().get(0).get(0)).isEqualTo(3);
    }
}
