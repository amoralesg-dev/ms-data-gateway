package com.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Solicitud de transacción con múltiples operaciones")
public class TransactionRequest {
    @NotEmpty(message = "Se requiere al menos una operación")
    private List<TransactionOperation> operations;
    
    @Data
    @Schema(description = "Operación individual dentro de una transacción")
    public static class TransactionOperation {
        @Schema(description = "Tipo de operación", allowableValues = {"INSERT", "UPDATE", "DELETE", "SELECT"})
        private String type;
        
        @Schema(description = "ID de la consulta/operación registrada")
        private String queryId;
        
        @Schema(description = "Parámetros de la operación")
        private Map<String, Object> params;
    }
}
