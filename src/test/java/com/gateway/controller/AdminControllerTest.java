package com.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.config.GatewayProperties;
import com.gateway.dto.AdminQueryRequest;
import com.gateway.model.ColumnInfo;
import com.gateway.model.QueryResult;
import com.gateway.security.ApiKeyAuthFilter;
import com.gateway.service.DataGatewayService;
import com.gateway.service.MetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DataGatewayService dataGatewayService;

    @MockBean
    private MetadataService metadataService;

    @MockBean
    private ApiKeyAuthFilter apiKeyAuthFilter;

    @MockBean
    private GatewayProperties gatewayProperties;

    @Test
    @DisplayName("POST /api/v1/admin/query - ejecuta SELECT administrativo con éxito")
    void executeAdminQuery_success() throws Exception {
        QueryResult mockResult = QueryResult.builder()
                .success(true)
                .columns(List.of(ColumnInfo.builder().name("tbl").type("VARCHAR").build()))
                .rows(List.of(List.of("SYSTABLES")))
                .totalRows(1)
                .page(1)
                .pageSize(100)
                .executionTimeMs(15L)
                .build();

        when(dataGatewayService.executeAdminQuery(any(), anyString(), anyString())).thenReturn(mockResult);

        AdminQueryRequest req = new AdminQueryRequest();
        req.setSql("SELECT * FROM SYSPROGRESS.SYSTABLES");

        mockMvc.perform(post("/api/v1/admin/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.columns[0].name").value("tbl"))
                .andExpect(jsonPath("$.rows[0][0]").value("SYSTABLES"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/query - retorna 400 en caso de violación de seguridad")
    void executeAdminQuery_securityViolation() throws Exception {
        QueryResult error = QueryResult.error("SECURITY_VIOLATION", "Operación no autorizada. Palabra clave prohibida detectada: DROP");
        when(dataGatewayService.executeAdminQuery(any(), anyString(), anyString())).thenReturn(error);

        AdminQueryRequest req = new AdminQueryRequest();
        req.setSql("DROP TABLE users");

        mockMvc.perform(post("/api/v1/admin/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SECURITY_VIOLATION"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/schemas - devuelve lista de esquemas")
    void listSchemas_success() throws Exception {
        when(metadataService.listSchemas()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/schemas"))
                .andExpect(status().isOk());
    }
}
