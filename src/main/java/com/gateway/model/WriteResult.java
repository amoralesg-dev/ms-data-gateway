package com.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado estándar de una operación de escritura")
public class WriteResult {
    private boolean success;
    private Integer rowsAffected;
    private String errorCode;
    private String message;
    private Long executionTimeMs;
    private String operationId;
    
    public static WriteResult error(String errorCode, String message) {
        return WriteResult.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
    
    public static WriteResult success(int rowsAffected, long executionTimeMs, String operationId) {
        return WriteResult.builder()
                .success(true)
                .rowsAffected(rowsAffected)
                .executionTimeMs(executionTimeMs)
                .operationId(operationId)
                .build();
    }
}
