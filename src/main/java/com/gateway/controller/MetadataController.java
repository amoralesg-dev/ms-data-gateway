package com.gateway.controller;

import com.gateway.model.*;
import com.gateway.service.MetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
@Tag(name = "Metadata", description = "Descubrimiento y metadatos de la base de datos OpenEdge")
@SecurityRequirement(name = "ApiKeyAuth")
public class MetadataController {

    private final MetadataService metadataService;

    @GetMapping("/schemas")
    @Operation(summary = "Listar esquemas", description = "Retorna todos los esquemas disponibles en la base de datos")
    public ResponseEntity<List<SchemaInfo>> listSchemas() throws SQLException {
        return ResponseEntity.ok(metadataService.listSchemas());
    }

    @GetMapping("/tables")
    @Operation(summary = "Listar tablas", description = "Retorna las tablas disponibles, opcionalmente filtradas por esquema")
    public ResponseEntity<List<TableInfo>> listTables(
            @Parameter(description = "Esquema a filtrar") @RequestParam(required = false) String schema,
            @Parameter(description = "Patrón de nombre de tabla (usa % como wildcard)") 
            @RequestParam(required = false) String pattern) throws SQLException {
        return ResponseEntity.ok(metadataService.listTables(schema, pattern));
    }

    @GetMapping("/tables/{tableName}/columns")
    @Operation(summary = "Listar columnas de una tabla")
    public ResponseEntity<List<ColumnInfo>> listColumns(
            @Parameter(description = "Nombre de la tabla") @PathVariable String tableName,
            @Parameter(description = "Esquema de la tabla") @RequestParam(required = false) String schema) throws SQLException {
        return ResponseEntity.ok(metadataService.listColumns(schema, tableName));
    }

    @GetMapping("/tables/{tableName}/primary-keys")
    @Operation(summary = "Obtener claves primarias de una tabla")
    public ResponseEntity<List<PrimaryKeyInfo>> getPrimaryKeys(
            @PathVariable String tableName,
            @RequestParam(required = false) String schema) throws SQLException {
        return ResponseEntity.ok(metadataService.getPrimaryKeys(schema, tableName));
    }

    @GetMapping("/tables/{tableName}/indexes")
    @Operation(summary = "Obtener índices de una tabla")
    public ResponseEntity<List<IndexInfo>> getIndexes(
            @PathVariable String tableName,
            @RequestParam(required = false) String schema) throws SQLException {
        return ResponseEntity.ok(metadataService.getIndexes(schema, tableName));
    }

    @GetMapping("/tables/{tableName}/foreign-keys")
    @Operation(summary = "Obtener foreign keys de una tabla")
    public ResponseEntity<List<ForeignKeyInfo>> getForeignKeys(
            @PathVariable String tableName,
            @RequestParam(required = false) String schema) throws SQLException {
        return ResponseEntity.ok(metadataService.getForeignKeys(schema, tableName));
    }

    @GetMapping("/jdbc-types")
    @Operation(summary = "Listar tipos JDBC disponibles")
    public ResponseEntity<List<String>> getJdbcTypes() {
        return ResponseEntity.ok(metadataService.getJdbcTypeNames());
    }

    @GetMapping("/database-info")
    @Operation(summary = "Información del servidor de base de datos")
    public ResponseEntity<String> getDatabaseInfo() throws SQLException {
        return ResponseEntity.ok(metadataService.getDatabaseInfo());
    }
}
