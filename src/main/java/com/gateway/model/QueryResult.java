package com.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado estándar de una consulta")
public class QueryResult {
    @Schema(description = "Indica si la operación fue exitosa")
    private boolean success;
    
    @Schema(description = "Columnas del resultado")
    private List<ColumnInfo> columns;
    
    @Schema(description = "Filas de datos")
    private List<List<Object>> rows;
    
    @Schema(description = "Total de registros")
    private Integer totalRows;
    
    @Schema(description = "Página actual (paginación)")
    private Integer page;
    
    @Schema(description = "Tamaño de página")
    private Integer pageSize;
    
    @Schema(description = "Total de páginas")
    private Integer totalPages;
    
    @Schema(description = "Tiempo de ejecución en milisegundos")
    private Long executionTimeMs;
    
    @Schema(description = "Código de error si success=false")
    private String errorCode;
    
    @Schema(description = "Mensaje de error si success=false")
    private String message;
    
    public static QueryResult error(String errorCode, String message) {
        return QueryResult.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}
