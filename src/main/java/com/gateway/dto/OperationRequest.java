package com.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
@Schema(description = "Solicitud de operación de escritura registrada")
public class OperationRequest {
    @Schema(description = "Parámetros de la operación")
    private Map<String, Object> params;
}
