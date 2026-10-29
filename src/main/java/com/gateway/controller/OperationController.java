package com.gateway.controller;

import com.gateway.dto.OperationRequest;
import com.gateway.model.WriteResult;
import com.gateway.service.DataGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
@Tag(name = "Operations", description = "Operaciones de escritura registradas (INSERT, UPDATE, DELETE)")
@SecurityRequirement(name = "ApiKeyAuth")
public class OperationController {

    private final DataGatewayService dataGatewayService;

    @PostMapping("/{operationId}")
    @Operation(
        summary = "Ejecutar operación registrada",
        description = "Ejecuta una operación de escritura (INSERT, UPDATE, DELETE) registrada en la configuración. " +
                      "Los parámetros se envían en el cuerpo JSON."
    )
    public ResponseEntity<WriteResult> executeOperation(
            @Parameter(description = "ID de la operación registrada", example = "update-supplier") 
            @PathVariable String operationId,
            @RequestBody OperationRequest request) {
        WriteResult result = dataGatewayService.executeRegisteredOperation(operationId, request.getParams());
        return result.isSuccess() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}
