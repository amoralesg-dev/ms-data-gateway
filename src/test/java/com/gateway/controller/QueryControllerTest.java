package com.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.config.GatewayProperties;
import com.gateway.dto.QueryRequest;
import com.gateway.model.ColumnInfo;
import com.gateway.model.QueryResult;
import com.gateway.security.ApiKeyAuthFilter;
import com.gateway.service.DataGatewayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(controllers = QueryController.class)
@AutoConfigureMockMvc(addFilters = false)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DataGatewayService dataGatewayService;

    @MockBean
    private ApiKeyAuthFilter apiKeyAuthFilter;

    @MockBean
    private GatewayProperties gatewayProperties;

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/queries/execute - retorna 200 en ejecucion exitosa")
    void executeQuery_success_returns200() throws Exception {
        QueryResult mockResult = QueryResult.builder()
                .success(true)
                .columns(List.of(
                        ColumnInfo.builder().name("ad_addr").type("VARCHAR").build(),
                        ColumnInfo.builder().name("ad_name").type("VARCHAR").build()
                ))
                .rows(List.of(
                        List.of("000001", "Proveedor A"),
                        List.of("000002", "Proveedor B")
                ))
                .totalRows(2)
                .page(1)
                .pageSize(50)
                .totalPages(1)
                .executionTimeMs(45L)
                .build();

        when(dataGatewayService.executeRegisteredQuery(any())).thenReturn(mockResult);

        QueryRequest request = new QueryRequest();
        request.setQueryId("list-suppliers");

        mockMvc.perform(post("/api/v1/queries/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.columns[0].name").value("ad_addr"))
                .andExpect(jsonPath("$.columns[1].name").value("ad_name"))
                .andExpect(jsonPath("$.rows[0][0]").value("000001"))
                .andExpect(jsonPath("$.executionTimeMs").value(45));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/queries/execute - retorna 400 cuando query no existe")
    void executeQuery_queryNotFound_returns400() throws Exception {
        QueryResult errorResult = QueryResult.error("QUERY_NOT_FOUND", "Query not found: bad-query");
        when(dataGatewayService.executeRegisteredQuery(any())).thenReturn(errorResult);

        QueryRequest request = new QueryRequest();
        request.setQueryId("bad-query");

        mockMvc.perform(post("/api/v1/queries/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("QUERY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Query not found: bad-query"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/queries/execute - retorna 400 cuando queryId esta vacio")
    void executeQuery_emptyQueryId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/queries/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryId\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/queries/execute - retorna 400 cuando no hay body")
    void executeQuery_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/queries/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/queries/{queryId} - ejecuta query via GET")
    void executeQueryGet_success() throws Exception {
        QueryResult mockResult = QueryResult.builder()
                .success(true)
                .columns(List.of(ColumnInfo.builder().name("ad_addr").type("VARCHAR").build()))
                .rows(List.of(List.of("000001")))
                .totalRows(1)
                .executionTimeMs(30L)
                .build();

        when(dataGatewayService.executeRegisteredQuery(any())).thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/queries/list-suppliers")
                        .param("prefix", "0001%")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.totalRows").value(1));
    }
}
