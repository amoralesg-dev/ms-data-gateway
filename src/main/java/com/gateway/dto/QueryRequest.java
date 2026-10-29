package com.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.Map;

@Data
@Schema(description = "Solicitud de ejecución de consulta registrada")
public class QueryRequest {
    @NotBlank(message = "queryId es requerido")
    @Schema(description = "Identificador de la consulta registrada", example = "list-suppliers")
    private String queryId;
    
    @Schema(description = "Parámetros de la consulta")
    private Map<String, Object> params;
    
    @Positive
    @Schema(description = "Número de página (1-based)", defaultValue = "1")
    private Integer page = 1;
    
    @Positive
    @Schema(description = "Registros por página", defaultValue = "50")
    private Integer pageSize = 50;
}
