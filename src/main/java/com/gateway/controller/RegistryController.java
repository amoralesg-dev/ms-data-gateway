package com.gateway.controller;

import com.gateway.config.GatewayProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Tag(name = "Registry", description = "Registro de consultas y operaciones disponibles")
@SecurityRequirement(name = "ApiKeyAuth")
public class RegistryController {

    private final GatewayProperties gatewayProperties;

    @GetMapping
    @Operation(summary = "Listar todas las consultas y operaciones registradas")
    public ResponseEntity<Map<String, GatewayProperties.RegisteredQuery>> listRegistered() {
        return ResponseEntity.ok(gatewayProperties.getRegisteredQueries());
    }

    @GetMapping("/{queryId}")
    @Operation(summary = "Obtener detalle de una consulta/operación registrada")
    public ResponseEntity<GatewayProperties.RegisteredQuery> getRegistered(@PathVariable String queryId) {
        GatewayProperties.RegisteredQuery query = gatewayProperties.getRegisteredQueries().get(queryId);
        if (query == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(query);
    }
}
