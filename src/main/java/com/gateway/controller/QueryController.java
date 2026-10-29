package com.gateway.controller;

import com.gateway.dto.QueryRequest;
import com.gateway.model.QueryResult;
import com.gateway.service.DataGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/queries")
@RequiredArgsConstructor
@Tag(name = "Queries", description = "Ejecución de consultas SELECT registradas")
@SecurityRequirement(name = "ApiKeyAuth")
public class QueryController {

    private final DataGatewayService dataGatewayService;

    @PostMapping("/execute")
    @Operation(
        summary = "Ejecutar consulta registrada",
        description = "Ejecuta una consulta SELECT previamente registrada en la configuración del servicio. " +
                      "Soporta parámetros nombrados y paginación."
    )
    public ResponseEntity<QueryResult> executeQuery(@Valid @RequestBody QueryRequest request) {
        QueryResult result = dataGatewayService.executeRegisteredQuery(request);
        return result.isSuccess() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @GetMapping("/{queryId}")
    @Operation(
        summary = "Ejecutar consulta registrada (GET)",
        description = "Ejecuta una consulta SELECT mediante GET con parámetros en query string"
    )
    public ResponseEntity<QueryResult> executeQueryGet(
            @Parameter(description = "ID de la consulta registrada") @PathVariable String queryId,
            @Parameter(description = "Parámetros como query params") @RequestParam Map<String, Object> params,
            @Parameter(description = "Número de página") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "50") Integer pageSize) {
        QueryRequest request = new QueryRequest();
        request.setQueryId(queryId);
        request.setParams(params);
        request.setPage(page);
        request.setPageSize(pageSize);
        QueryResult result = dataGatewayService.executeRegisteredQuery(request);
        return result.isSuccess() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}
