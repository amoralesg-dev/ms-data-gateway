package com.gateway.controller;

import com.gateway.dto.TransactionRequest;
import com.gateway.model.TransactionResult;
import com.gateway.service.DataGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/transaction")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Ejecución de múltiples operaciones en una sola transacción")
@SecurityRequirement(name = "ApiKeyAuth")
public class TransactionController {

    private final DataGatewayService dataGatewayService;

    @PostMapping
    @Operation(
        summary = "Ejecutar transacción",
        description = "Ejecuta múltiples operaciones registradas dentro de una misma transacción de base de datos. " +
                      "Si alguna operación falla, se realiza rollback completo."
    )
    public ResponseEntity<TransactionResult> executeTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionResult result = dataGatewayService.executeTransaction(request);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
