package com.gateway.controller;

import com.gateway.catalog.GatewayCatalogRepository;
import com.gateway.catalog.GatewayDatasourceEntity;
import com.gateway.catalog.GatewayQueryEntity;
import com.gateway.catalog.GatewayQueryParameterEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador administrativo de mantenimiento completo del catálogo.
 * Protegido exclusivamente por ROLE_ADMIN y cabecera X-Admin-Key.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gateway/admin")
@RequiredArgsConstructor
@Tag(name = "Gateway Admin Catalog", description = "CRUD administrativo de DataSources, Queries y Parámetros")
@SecurityRequirement(name = "AdminKeyAuth")
public class GatewayAdminCatalogController {

    private final GatewayCatalogRepository catalogRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. CRUD DATASOURCES
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping("/datasources")
    @Operation(summary = "Crear nuevo DataSource en el catálogo")
    public ResponseEntity<?> createDatasource(@Valid @RequestBody GatewayDatasourceEntity ds) {
        try {
            com.gateway.dialect.EngineType.valueOf(ds.getEngineType().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Motor no soportado: " + ds.getEngineType() + ". Motores válidos: OPENEDGE, MYSQL, POSTGRESQL, SQLSERVER, ORACLE"));
        }

        if (catalogRepository.findDatasourceById(ds.getDatasourceId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe un DataSource con datasourceId: " + ds.getDatasourceId()));
        }
        if (catalogRepository.findDatasourceByCode(ds.getDatasourceCode()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe un DataSource con datasourceCode: " + ds.getDatasourceCode()));
        }

        if (ds.isDefaultFlag()) {
            catalogRepository.clearDefaultFlag();
        }

        catalogRepository.insertDatasource(ds);
        return ResponseEntity.status(HttpStatus.CREATED).body(ds);
    }

    @PutMapping("/datasources/{datasourceId}")
    @Operation(summary = "Actualizar DataSource existente")
    public ResponseEntity<?> updateDatasource(@PathVariable String datasourceId, @Valid @RequestBody GatewayDatasourceEntity ds) {
        Optional<GatewayDatasourceEntity> existing = catalogRepository.findDatasourceById(datasourceId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (ds.getEngineType() != null) {
            try {
                com.gateway.dialect.EngineType.valueOf(ds.getEngineType().toUpperCase());
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Motor no soportado: " + ds.getEngineType() + ". Motores válidos: OPENEDGE, MYSQL, POSTGRESQL, SQLSERVER, ORACLE"));
            }
        }

        // Validar unicidad de código
        Optional<GatewayDatasourceEntity> byCode = catalogRepository.findDatasourceByCode(ds.getDatasourceCode());
        if (byCode.isPresent() && !byCode.get().getDatasourceId().equals(datasourceId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El datasourceCode '" + ds.getDatasourceCode() + "' ya está en uso por otro datasource."));
        }

        if (ds.isDefaultFlag() && !existing.get().isDefaultFlag()) {
            catalogRepository.clearDefaultFlag();
        }

        catalogRepository.updateDatasource(datasourceId, ds);
        return ResponseEntity.ok(ds);
    }

    @DeleteMapping("/datasources/{datasourceId}")
    @Operation(summary = "Eliminar DataSource si no tiene consultas asociadas")
    public ResponseEntity<?> deleteDatasource(@PathVariable String datasourceId) {
        if (catalogRepository.findDatasourceById(datasourceId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int queryCount = catalogRepository.countQueriesByDatasourceId(datasourceId);
        if (queryCount > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "No es posible eliminar el datasource. Existen " + queryCount + " consultas asociadas."));
        }

        catalogRepository.deleteDatasource(datasourceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/datasources")
    @Operation(summary = "Listar todos los DataSources registrados")
    public ResponseEntity<List<GatewayDatasourceEntity>> listDatasources() {
        return ResponseEntity.ok(catalogRepository.findAllDatasources());
    }

    @GetMapping("/datasources/{datasourceId}")
    @Operation(summary = "Obtener detalle de un DataSource")
    public ResponseEntity<?> getDatasource(@PathVariable String datasourceId) {
        return catalogRepository.findDatasourceById(datasourceId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. CRUD QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping("/queries")
    @Operation(summary = "Registrar nueva consulta en el catálogo")
    public ResponseEntity<?> createQuery(@Valid @RequestBody GatewayQueryEntity query, Authentication auth) {
        if (catalogRepository.findQueryByCode(query.getQueryCode()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe una consulta con queryCode: " + query.getQueryCode()));
        }
        if (catalogRepository.findDatasourceById(query.getDatasourceId()).isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El datasourceId especificado no existe: " + query.getDatasourceId()));
        }

        String user = auth != null ? auth.getName() : "ADMIN";
        query.setCreatedBy(user);
        catalogRepository.insertQuery(query);
        return ResponseEntity.status(HttpStatus.CREATED).body(query);
    }

    @PutMapping("/queries/{queryCode}")
    @Operation(summary = "Modificar SQL o metadatos de una consulta registrada en caliente")
    public ResponseEntity<?> updateQuery(@PathVariable String queryCode, @Valid @RequestBody GatewayQueryEntity query, Authentication auth) {
        if (catalogRepository.findQueryByCode(queryCode).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (catalogRepository.findDatasourceById(query.getDatasourceId()).isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El datasourceId especificado no existe: " + query.getDatasourceId()));
        }

        String user = auth != null ? auth.getName() : "ADMIN";
        catalogRepository.updateQuery(queryCode, query, user);
        return ResponseEntity.ok(query);
    }

    @PatchMapping("/queries/{queryCode}/toggle")
    @Operation(summary = "Habilitar o deshabilitar consulta en caliente sin redeploy")
    public ResponseEntity<?> toggleQuery(@PathVariable String queryCode, @RequestParam boolean enabled, Authentication auth) {
        if (catalogRepository.findQueryByCode(queryCode).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String user = auth != null ? auth.getName() : "ADMIN";
        catalogRepository.toggleQueryEnabled(queryCode, enabled, user);
        return ResponseEntity.ok(Map.of("queryCode", queryCode, "enabled", enabled));
    }

    @DeleteMapping("/queries/{queryCode}")
    @Operation(summary = "Eliminar consulta registrada del catálogo")
    public ResponseEntity<?> deleteQuery(@PathVariable String queryCode) {
        if (catalogRepository.findQueryByCode(queryCode).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        catalogRepository.deleteQuery(queryCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/queries")
    @Operation(summary = "Listar todas las consultas del catálogo administrativo")
    public ResponseEntity<List<GatewayQueryEntity>> listQueries() {
        return ResponseEntity.ok(catalogRepository.findAllQueries());
    }

    @GetMapping("/queries/{queryCode}")
    @Operation(summary = "Obtener detalle completo de una consulta y sus parámetros")
    public ResponseEntity<?> getQuery(@PathVariable String queryCode) {
        Optional<GatewayQueryEntity> opt = catalogRepository.findQueryByCode(queryCode);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<GatewayQueryParameterEntity> params = catalogRepository.findParametersByQueryCode(queryCode);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("query", opt.get());
        map.put("parameters", params);
        return ResponseEntity.ok(map);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. CRUD PARÁMETROS DE CONSULTA
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/queries/{queryCode}/parameters")
    @Operation(summary = "Listar parámetros de una consulta")
    public ResponseEntity<?> listParameters(@PathVariable String queryCode) {
        if (catalogRepository.findQueryByCode(queryCode).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(catalogRepository.findParametersByQueryCode(queryCode));
    }

    @PostMapping("/queries/{queryCode}/parameters")
    @Operation(summary = "Agregar parámetro a una consulta registrada")
    public ResponseEntity<?> addParameter(@PathVariable String queryCode, @Valid @RequestBody GatewayQueryParameterEntity param) {
        if (catalogRepository.findQueryByCode(queryCode).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (catalogRepository.findParameterByName(queryCode, param.getParameterName()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe el parámetro '" + param.getParameterName() + "' para la consulta " + queryCode));
        }
        param.setQueryCode(queryCode);
        catalogRepository.insertParameter(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(param);
    }

    @PutMapping("/queries/{queryCode}/parameters/{parameterName}")
    @Operation(summary = "Actualizar definición de parámetro por nombre")
    public ResponseEntity<?> updateParameterByName(@PathVariable String queryCode, @PathVariable String parameterName, @Valid @RequestBody GatewayQueryParameterEntity param) {
        Optional<GatewayQueryParameterEntity> existing = catalogRepository.findParameterByName(queryCode, parameterName);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        param.setId(existing.get().getId());
        param.setQueryCode(queryCode);
        param.setParameterName(parameterName);
        catalogRepository.updateParameterByName(queryCode, parameterName, param);
        return ResponseEntity.ok(param);
    }

    @DeleteMapping("/queries/{queryCode}/parameters/{parameterName}")
    @Operation(summary = "Eliminar parámetro de una consulta por nombre")
    public ResponseEntity<?> deleteParameterByName(@PathVariable String queryCode, @PathVariable String parameterName) {
        Optional<GatewayQueryParameterEntity> existing = catalogRepository.findParameterByName(queryCode, parameterName);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        catalogRepository.deleteParameterByName(queryCode, parameterName);
        return ResponseEntity.noContent().build();
    }
}
