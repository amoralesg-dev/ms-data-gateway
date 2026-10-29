package com.gateway.qad.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.catalog.GatewayCatalogRepository;
import com.gateway.catalog.GatewayServiceEntity;
import com.gateway.catalog.GatewayServiceExecutionEntity;
import com.gateway.qad.client.QadSoapClient;
import com.gateway.qad.client.QadSoapClient.SoapCallResult;
import com.gateway.qad.dto.BCreditorRequest;
import com.gateway.qad.dto.QadServiceResponse;
import com.gateway.qad.envelope.QadSessionContext;
import com.gateway.qad.envelope.QadSoapEnvelopeBuilder;
import com.gateway.qad.mapper.QadBusinessMapper;
import com.gateway.qad.parser.QadSoapResponseParser;
import com.gateway.qad.registry.QadServiceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Servicio orquestador central del motor QadSoapEngine.
 * Ejecuta el ciclo de vida completo:
 * DTO -> Mapper -> SoapEnvelopeBuilder -> QadSoapClient -> QadSoapResponseParser -> Auditoría en MySQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QadSoapEngineService {

    private final QadSoapClient soapClient;
    private final QadSoapEnvelopeBuilder envelopeBuilder;
    private final QadSoapResponseParser responseParser;
    private final QadServiceRegistry serviceRegistry;
    private final Optional<GatewayCatalogRepository> catalogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Ejecuta una operación de negocio QAD a partir de su DTO tipado.
     *
     * @param serviceCode Código del servicio (ej. BCREDITOR)
     * @param requestDto Objeto DTO del cliente REST
     * @param executedBy Usuario o API Key autenticada
     * @param sourceIp IP origen del cliente
     * @return QadServiceResponse mapeada a formato REST
     */
    @SuppressWarnings("unchecked")
    public <T> QadServiceResponse executeService(String serviceCode, T requestDto, String executedBy, String sourceIp) {
        long startTime = System.currentTimeMillis();
        String jsonPayload = "";
        try {
            jsonPayload = objectMapper.writeValueAsString(requestDto);
        } catch (Exception ignored) {}

        log.info("Iniciando ejecución de servicio QAD: serviceCode={}, executedBy={}", serviceCode, executedBy);

        QadBusinessMapper<T> mapper = (QadBusinessMapper<T>) serviceRegistry.getMapper(serviceCode);
        if (mapper == null) {
            String errorMsg = "Servicio QAD no soportado o mapper no registrado: " + serviceCode;
            log.error(errorMsg);
            auditExecution(serviceCode, jsonPayload, "", "ERROR", "SERVICE_NOT_FOUND", errorMsg, 0, executedBy, sourceIp);
            return QadServiceResponse.technicalError("SERVICE_NOT_FOUND", errorMsg, System.currentTimeMillis() - startTime);
        }

        // Consultar configuración del catálogo en MySQL si existe
        Optional<GatewayServiceEntity> serviceEntity = serviceRegistry.getServiceEntity(serviceCode);
        if (serviceEntity.isPresent() && !serviceEntity.get().isEnabled()) {
            String errorMsg = "El servicio QAD '" + serviceCode + "' se encuentra deshabilitado en el catálogo";
            log.warn(errorMsg);
            auditExecution(serviceCode, jsonPayload, "", "ERROR", "SERVICE_DISABLED", errorMsg, 0, executedBy, sourceIp);
            return QadServiceResponse.technicalError("SERVICE_DISABLED", errorMsg, System.currentTimeMillis() - startTime);
        }

        String targetEndpoint = serviceEntity.map(GatewayServiceEntity::getEndpointUrl).orElse(null);
        int timeoutSeconds = serviceEntity.map(GatewayServiceEntity::getTimeoutSeconds).orElse(60);

        try {
            // 1. Transformar DTO a XML interno y SessionContext
            String businessXml = mapper.toXmlPayload(requestDto);
            QadSessionContext sessionCtx = mapper.buildSessionContext(requestDto);

            // 2. Construir Envelope SOAP completo (incluyendo WS-Addressing y qcom:dsSessionContext)
            String soapEnvelope = envelopeBuilder.buildEnvelope(mapper.getOperationName(), sessionCtx, businessXml);

            // 3. Invocar HTTP SOAP
            SoapCallResult callResult = soapClient.sendSoapRequest(targetEndpoint, soapEnvelope, timeoutSeconds);

            // 4. Parsear respuesta (Success, Error Funcional dsExceptions o SOAP Fault)
            QadServiceResponse response = responseParser.parse(callResult.responseBody(), callResult.executionTimeMs());

            // 5. Registrar Auditoría
            String status = response.isSuccess() ? "SUCCESS" : ("SOAP_FAULT".equals(response.getErrorCode()) ? "FAULT" : "ERROR");
            auditExecution(serviceCode, jsonPayload, callResult.responseBody(), status, response.getErrorCode(), response.getMessage(), callResult.executionTimeMs(), executedBy, sourceIp);

            return response;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Excepción ejecutando servicio QAD {}: {}", serviceCode, e.getMessage(), e);
            auditExecution(serviceCode, jsonPayload, "", "FAULT", "EXECUTION_EXCEPTION", e.getMessage(), duration, executedBy, sourceIp);
            return QadServiceResponse.technicalError("EXECUTION_EXCEPTION", "Error invocando servicio QAD: " + e.getMessage(), duration);
        }
    }

    /**
     * Endpoint de conveniencia para la creación / actualización de acreedores (BCreditor).
     */
    public QadServiceResponse createOrUpdateCreditor(BCreditorRequest request, String executedBy, String sourceIp) {
        return executeService("BCREDITOR", request, executedBy, sourceIp);
    }

    private void auditExecution(String serviceCode, String reqPayload, String respPayload, String status,
                                String errorCode, String errorMessage, long executionTimeMs, String executedBy, String sourceIp) {
        if (catalogRepository.isPresent()) {
            try {
                GatewayServiceExecutionEntity entity = GatewayServiceExecutionEntity.builder()
                        .serviceCode(serviceCode)
                        .requestPayload(reqPayload)
                        .responsePayload(respPayload)
                        .status(status)
                        .errorCode(errorCode)
                        .errorMessage(errorMessage)
                        .executionTimeMs(executionTimeMs)
                        .executedBy(executedBy != null ? executedBy : "api-client")
                        .sourceIp(sourceIp)
                        .build();
                catalogRepository.get().recordServiceExecution(entity);
            } catch (Exception ex) {
                log.error("Error al registrar auditoría de servicio QAD: {}", ex.getMessage());
            }
        }
    }
}

