package com.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.catalog.GatewayCatalogRepository;
import com.gateway.catalog.GatewayDatasourceEntity;
import com.gateway.catalog.GatewayQueryEntity;
import com.gateway.catalog.GatewayQueryParameterEntity;
import com.gateway.config.GatewayProperties;
import com.gateway.config.SecurityConfig;
import com.gateway.security.ApiKeyAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GatewayAdminCatalogController.class)
@Import({SecurityConfig.class, ApiKeyAuthFilter.class})
class GatewayAdminCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GatewayCatalogRepository catalogRepository;

    @MockBean
    private GatewayProperties gatewayProperties;

    private static final String ADMIN_KEY = "test-admin-secret-key";
    private static final String CLIENT_KEY = "test-client-key";

    @BeforeEach
    void setUp() {
        GatewayProperties.Security sec = new GatewayProperties.Security();
        sec.setApiKeyHeader("X-API-Key");
        sec.setAdminKeyHeader("X-Admin-Key");
        sec.setAdminKeys(List.of(ADMIN_KEY));
        sec.setApiKeys(List.of(CLIENT_KEY));
        when(gatewayProperties.getSecurity()).thenReturn(sec);
    }

    @Test
    @DisplayName("Sin X-Admin-Key debe retornar 403 Forbidden")
    void testAccessDeniedWithoutAdminKey() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/admin/datasources"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con X-API-Key normal pero sin X-Admin-Key debe retornar 403 Forbidden")
    void testAccessDeniedWithOnlyClientKey() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/admin/datasources")
                        .header("X-API-Key", CLIENT_KEY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Listar DataSources con X-Admin-Key exitoso")
    void testListDatasourcesSuccess() throws Exception {
        GatewayDatasourceEntity ds = GatewayDatasourceEntity.builder()
                .datasourceId("openedge-mfg")
                .datasourceCode("openedge-mfg")
                .datasourceName("OpenEdge MFG")
                .engineType("OPENEDGE")
                .enabled(true)
                .defaultFlag(true)
                .build();

        when(catalogRepository.findAllDatasources()).thenReturn(List.of(ds));

        mockMvc.perform(get("/api/v1/gateway/admin/datasources")
                        .header("X-Admin-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].datasourceId").value("openedge-mfg"))
                .andExpect(jsonPath("$[0].engineType").value("OPENEDGE"));
    }

    @Test
    @DisplayName("Crear DataSource exitoso con X-Admin-Key")
    void testCreateDatasourceSuccess() throws Exception {
        GatewayDatasourceEntity ds = GatewayDatasourceEntity.builder()
                .datasourceId("pg-warehouse")
                .datasourceCode("pg-wh")
                .datasourceName("PostgreSQL Warehouse")
                .engineType("POSTGRESQL")
                .enabled(true)
                .defaultFlag(false)
                .build();

        when(catalogRepository.findDatasourceById("pg-warehouse")).thenReturn(Optional.empty());
        when(catalogRepository.findDatasourceByCode("pg-wh")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/gateway/admin/datasources")
                        .header("X-Admin-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ds)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datasourceId").value("pg-warehouse"));

        verify(catalogRepository, times(1)).insertDatasource(any(GatewayDatasourceEntity.class));
    }

    @Test
    @DisplayName("Eliminar DataSource rechazada si tiene consultas asociadas (409 Conflict)")
    void testDeleteDatasourceConflictWhenQueriesExist() throws Exception {
        GatewayDatasourceEntity ds = GatewayDatasourceEntity.builder()
                .datasourceId("openedge-mfg")
                .build();

        when(catalogRepository.findDatasourceById("openedge-mfg")).thenReturn(Optional.of(ds));
        when(catalogRepository.countQueriesByDatasourceId("openedge-mfg")).thenReturn(5);

        mockMvc.perform(delete("/api/v1/gateway/admin/datasources/openedge-mfg")
                        .header("X-Admin-Key", ADMIN_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("No es posible eliminar el datasource. Existen 5 consultas asociadas."));

        verify(catalogRepository, never()).deleteDatasource("openedge-mfg");
    }

    @Test
    @DisplayName("Toggle consulta habilitada/deshabilitada exitoso")
    void testToggleQuerySuccess() throws Exception {
        GatewayQueryEntity query = GatewayQueryEntity.builder()
                .queryCode("supplier-by-id")
                .enabled(true)
                .build();

        when(catalogRepository.findQueryByCode("supplier-by-id")).thenReturn(Optional.of(query));

        mockMvc.perform(patch("/api/v1/gateway/admin/queries/supplier-by-id/toggle?enabled=false")
                        .header("X-Admin-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryCode").value("supplier-by-id"))
                .andExpect(jsonPath("$.enabled").value(false));

        verify(catalogRepository, times(1)).toggleQueryEnabled(eq("supplier-by-id"), eq(false), any());
    }

    @Test
    @DisplayName("Agregar parámetro a consulta exitoso")
    void testAddParameterSuccess() throws Exception {
        GatewayQueryEntity query = GatewayQueryEntity.builder()
                .queryCode("supplier-by-id")
                .build();

        when(catalogRepository.findQueryByCode("supplier-by-id")).thenReturn(Optional.of(query));

        GatewayQueryParameterEntity param = GatewayQueryParameterEntity.builder()
                .parameterName("supplierId")
                .parameterType("STRING")
                .requiredFlag(true)
                .build();

        mockMvc.perform(post("/api/v1/gateway/admin/queries/supplier-by-id/parameters")
                        .header("X-Admin-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(param)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parameterName").value("supplierId"));

        verify(catalogRepository, times(1)).insertParameter(any(GatewayQueryParameterEntity.class));
    }
}