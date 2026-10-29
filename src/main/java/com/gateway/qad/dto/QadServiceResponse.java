package com.gateway.qad.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta estándar de ejecución de servicio de negocio QAD SOAP")
public class QadServiceResponse {

    @Schema(description = "Indica si la ejecución de negocio fue exitosa en QAD", example = "true")
    private boolean success;

    @Schema(description = "Código de error funcional de QAD (ej. GEN-O-2) o técnico (SOAP_FAULT, SERVICE_ERROR)", example = "GEN-O-2")
    private String errorCode;

    @Schema(description = "Mensaje descriptivo del resultado o error de QAD", example = "Proveedor 60000011 procesado correctamente")
    private String message;

    @Schema(description = "Lista detallada de excepciones o errores de validación de QAD")
    private List<QadExceptionDetail> exceptions;

    @Schema(description = "Metadatos y contexto de sesión devuelto por QAD (apiName, domain, sessionId, etc.)")
    private Map<String, Object> context;

    @Schema(description = "Cuerpo de datos estructurado devuelto por la operación")
    private Object data;

    @Schema(description = "Tiempo de ejecución en milisegundos", example = "420")
    private long executionTimeMs;

    public static QadServiceResponse success(String message, Object data, Map<String, Object> context, long executionTimeMs) {
        return QadServiceResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .context(context)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    public static QadServiceResponse functionalError(String errorCode, String message, List<QadExceptionDetail> exceptions, long executionTimeMs) {
        return QadServiceResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .exceptions(exceptions)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    public static QadServiceResponse technicalError(String errorCode, String message, long executionTimeMs) {
        return QadServiceResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Detalle de excepción de negocio devuelta por QAD (dsExceptions / temp_err_msg)")
    public static class QadExceptionDetail {
        private String messageNumber; // tt_msg_nbr (ej. GEN-O-2)
        private String messageDesc;   // tt_msg_desc
        private String messageField;  // tt_msg_field
        private String messageData;   // tt_msg_data
        private String messageSev;    // tt_msg_sev
        private String messageContext;// tt_msg_context
        private Integer level;        // tt_level
    }
}

