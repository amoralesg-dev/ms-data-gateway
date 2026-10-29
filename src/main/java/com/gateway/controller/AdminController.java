package com.gateway.controller;

import com.gateway.dto.AdminQueryRequest;
import com.gateway.model.ColumnInfo;
import com.gateway.model.QueryResult;
import com.gateway.model.SchemaInfo;
import com.gateway.model.TableInfo;
import com.gateway.service.DataGatewayService;
import com.gateway.service.MetadataService;
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

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador de Administración y Descubrimiento para OpenEdge / RDBMS.
 * Protegido exclusivamente con X-Admin-Key / ROLE_ADMIN.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Endpoints administrativos para exploración libre controlada y metadatos")
@SecurityRequirement(name = "AdminKeyAuth")
public class AdminController {

    private final DataGatewayService dataGatewayService;
    private final MetadataService metadataService;

    @PostMapping("/query")
    @Operation(
        summary = "Ejecutar consulta administrativa libre controlada",
        description = "Permite ejecutar sentencias SELECT libres para análisis y descubrimiento. " +
                      "Bloquea estrictamente INSERT, UPDATE, DELETE, DROP, ALTER y múltiples sentencias."
    )
    public ResponseEntity<QueryResult> executeAdminQuery(
            @Valid @RequestBody AdminQueryRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {

        String username = authentication != null ? authentication.getName() : "anonymous-admin";
        String clientIp = servletRequest.getRemoteAddr();

        QueryResult result = dataGatewayService.executeAdminQuery(request, username, clientIp);

        return result.isSuccess() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @GetMapping("/tables")
    @Operation(summary = "Listar tablas y vistas del catálogo")
    public ResponseEntity<List<TableInfo>> listTables(
            @Parameter(description = "Esquema (opcional)") @RequestParam(required = false) String schema,
            @Parameter(description = "Patrón de nombre de tabla (opcional)") @RequestParam(required = false) String tableNamePattern)
            throws SQLException {
        return ResponseEntity.ok(metadataService.listTables(schema, tableNamePattern));
    }

    @GetMapping("/tables/{tableName}/columns")
    @Operation(summary = "Listar columnas de una tabla específica")
    public ResponseEntity<List<ColumnInfo>> listColumns(
            @Parameter(description = "Nombre de la tabla") @PathVariable String tableName,
            @Parameter(description = "Esquema (opcional)") @RequestParam(required = false) String schema)
            throws SQLException {
        return ResponseEntity.ok(metadataService.listColumns(schema, tableName));
    }

    @GetMapping("/schemas")
    @Operation(summary = "Listar esquemas de base de datos")
    public ResponseEntity<List<SchemaInfo>> listSchemas() throws SQLException {
        return ResponseEntity.ok(metadataService.listSchemas());
    }

    @GetMapping("/catalog")
    @Operation(summary = "Listar catálogos de base de datos")
    public ResponseEntity<List<String>> listCatalogs() throws SQLException {
        return ResponseEntity.ok(metadataService.listCatalogs());
    }
}
