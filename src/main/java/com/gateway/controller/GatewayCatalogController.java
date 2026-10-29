package com.gateway.controller;

import com.gateway.catalog.GatewayCatalogRepository;
import com.gateway.catalog.GatewayDatasourceEntity;
import com.gateway.catalog.GatewayQueryEntity;
import com.gateway.catalog.GatewayQueryParameterEntity;
import com.gateway.dto.GatewayQueryRequest;
import com.gateway.dto.QueryRequest;
import com.gateway.model.QueryResult;
import com.gateway.service.DataGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador principal orientado al catálogo administrable del Gateway.
 * Provee endpoints unificados:
 * - POST /api/v1/gateway/query
 * - GET  /api/v1/gateway/queries
 * - GET  /api/v1/gateway/queries/{queryCode}
 * - GET  /api/v1/gateway/datasources
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gateway")
@RequiredArgsConstructor
@Tag(name = "Gateway Catalog", description = "Endpoints de ejecución y consulta orientados a catálogo administrable")
@SecurityRequirement(name = "ApiKeyAuth")
public class GatewayCatalogController {

    private final DataGatewayService dataGatewayService;
    private final Optional<GatewayCatalogRepository> catalogRepository;

    @PostMapping("/query")
    @Operation(
        summary = "Ejecutar consulta registrada en el catálogo",
        description = "Ejecuta una consulta SELECT por código registrada en el catálogo de base de datos o YAML. " +
                      "Aplica parámetros dinámicos, paginación y auditoría."
    )
    public ResponseEntity<QueryResult> executeQuery(
            @Valid @RequestBody GatewayQueryRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {

        String username = authentication != null ? authentication.getName() : "api-client";
        String clientIp = servletRequest.getRemoteAddr();

        QueryRequest legacyReq = new QueryRequest();
        legacyReq.setQueryId(request.getQueryCode());
        legacyReq.setParams(request.getParams());
        legacyReq.setPage(request.getPage());
        legacyReq.setPageSize(request.getPageSize());

        QueryResult result = dataGatewayService.executeRegisteredQuery(legacyReq, username, clientIp);
        return result.isSuccess() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @GetMapping("/queries")
    @Operation(summary = "Listar consultas registradas en el catálogo")
    public ResponseEntity<List<GatewayQueryEntity>> listQueries() {
        if (catalogRepository.isPresent()) {
            return ResponseEntity.ok(catalogRepository.get().findAllQueries());
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/queries/{queryCode}")
    @Operation(summary = "Obtener detalle y parámetros de una consulta registrada")
    public ResponseEntity<?> getQueryDetail(@Parameter(description = "Código de la consulta") @PathVariable String queryCode) {
        if (catalogRepository.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<GatewayQueryEntity> optQuery = catalogRepository.get().findQueryByCode(queryCode);
        if (optQuery.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<GatewayQueryParameterEntity> params = catalogRepository.get().findParametersByQueryCode(queryCode);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", optQuery.get());
        response.put("parameters", params);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/datasources")
    @Operation(summary = "Listar orígenes de datos registrados en el catálogo")
    public ResponseEntity<List<GatewayDatasourceEntity>> listDatasources() {
        if (catalogRepository.isPresent()) {
            return ResponseEntity.ok(catalogRepository.get().findAllDatasources());
        }
        return ResponseEntity.ok(List.of());
    }
}
