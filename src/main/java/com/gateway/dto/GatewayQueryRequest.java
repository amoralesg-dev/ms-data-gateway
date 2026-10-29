package com.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de ejecución de consulta orientada a catálogo")
public class GatewayQueryRequest {

    @NotBlank(message = "queryCode es requerido")
    @Schema(description = "Código identificador único de la consulta registrada", example = "example-list-suppliers")
    private String queryCode;

    @Schema(description = "Parámetros de la consulta")
    private Map<String, Object> params;

    @Positive(message = "page debe ser mayor a 0")
    @Schema(description = "Número de página (1-based)", defaultValue = "1")
    @Builder.Default
    private Integer page = 1;

    @Positive(message = "pageSize debe ser mayor a 0")
    @Schema(description = "Registros por página", defaultValue = "50")
    @Builder.Default
    private Integer pageSize = 50;
}
