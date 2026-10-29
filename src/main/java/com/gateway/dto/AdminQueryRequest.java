package com.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Solicitud de consulta administrativa libre controlada")
public class AdminQueryRequest {

    @NotBlank(message = "La consulta SQL no puede estar vacía")
    @Schema(description = "Sentencia SQL SELECT a ejecutar", example = "SELECT * FROM SYSPROGRESS.SYSTABLES")
    private String sql;

    @Positive(message = "El número de página debe ser mayor a 0")
    @Schema(description = "Número de página (1-based)", defaultValue = "1")
    private Integer page = 1;

    @Positive(message = "El tamaño de página debe ser mayor a 0")
    @Schema(description = "Registros por página", defaultValue = "100")
    private Integer pageSize = 100;
}
