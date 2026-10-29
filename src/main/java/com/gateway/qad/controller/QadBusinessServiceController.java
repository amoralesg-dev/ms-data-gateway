package com.gateway.qad.controller;

import com.gateway.catalog.GatewayCatalogRepository;
import com.gateway.catalog.GatewayServiceEntity;
import com.gateway.qad.dto.BCreditorRequest;
import com.gateway.qad.dto.QadServiceResponse;
import com.gateway.qad.registry.QadServiceRegistry;
import com.gateway.qad.service.QadSoapEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controlador REST para operaciones de negocio QAD a través del motor SOAP QXtend
 * y administración de su catálogo técnico en gateway_service.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/qad")
@RequiredArgsConstructor
@Tag(name = "QAD Business Services", description = "Operaciones de negocio en QAD ERP mediante QXtend SOAP")
@SecurityRequirement(name = "ApiKeyAuth")
public class QadBusinessServiceController {

    private final QadSoapEngineService qadSoapEngineService;
    private final QadServiceRegistry serviceRegistry;
    private final Optional<GatewayCatalogRepository> catalogRepository;

    @PostMapping("/creditors")
    @Operation(
            summary = "Crear o actualizar Acreedor / Proveedor en QAD (BCreditor)",
            description = "Recibe un JSON simple, construye el Envelope SOAP con WS-Addressing y dsSessionContext, ejecuta la llamada a QXtend y retorna una respuesta JSON estructurada diferenciando éxito o errores de negocio."
    )
    public ResponseEntity<QadServiceResponse> createOrUpdateCreditor(
            @Valid @RequestBody BCreditorRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {

        String executedBy = authentication != null ? authentication.getName() : "api-client";
        String sourceIp = servletRequest.getRemoteAddr();

        QadServiceResponse response = qadSoapEngineService.createOrUpdateCreditor(request, executedBy, sourceIp);

        return mapToHttpResponse(response);
    }

    @GetMapping("/services")
    @Operation(
            summary = "Listar servicios de negocio QAD registrados en el catálogo",
            description = "Retorna los servicios disponibles soportados por el motor QadSoapEngine."
    )
    public ResponseEntity<List<GatewayServiceEntity>> listServices() {
        if (catalogRepository.isPresent()) {
            return ResponseEntity.ok(catalogRepository.get().findAllServices());
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/services/{serviceCode}")
    @Operation(summary = "Consultar detalle de un servicio de negocio QAD")
    public ResponseEntity<GatewayServiceEntity> getServiceDetail(
            @Parameter(description = "Código del servicio (ej. BCREDITOR)") @PathVariable String serviceCode) {
        if (catalogRepository.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return catalogRepository.get().findServiceByCode(serviceCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/services")
    @Operation(
            summary = "Registrar un nuevo servicio QAD en el catálogo",
            description = "Inserta un registro en gateway_service para habilitar un nuevo servicio de negocio en el gateway."
    )
    public ResponseEntity<?> registerService(@RequestBody GatewayServiceEntity entity) {
        if (catalogRepository.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Catálogo no disponible");
        }
        catalogRepository.get().insertService(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @PutMapping("/services/{serviceCode}")
    @Operation(
            summary = "Actualizar configuración de un servicio QAD en el catálogo",
            description = "Modifica URL de endpoint, timeout o estado habilitado en gateway_service."
    )
    public ResponseEntity<?> updateService(
            @PathVariable String serviceCode,
            @RequestBody GatewayServiceEntity entity) {
        if (catalogRepository.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Catálogo no disponible");
        }
        catalogRepository.get().updateService(serviceCode, entity);
        return ResponseEntity.ok(entity);
    }

    @DeleteMapping("/services/{serviceCode}")
    @Operation(
            summary = "Eliminar un servicio QAD del catálogo",
            description = "Remueve la configuración del servicio de la tabla gateway_service."
    )
    public ResponseEntity<?> deleteService(@PathVariable String serviceCode) {
        if (catalogRepository.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Catálogo no disponible");
        }
        catalogRepository.get().deleteService(serviceCode);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<QadServiceResponse> mapToHttpResponse(QadServiceResponse response) {
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }

        // Errores técnicos (SOAP Fault, timeout, excepción de red) -> HTTP 500
        if ("SOAP_FAULT".equalsIgnoreCase(response.getErrorCode()) ||
            "EXECUTION_EXCEPTION".equalsIgnoreCase(response.getErrorCode()) ||
            "XML_PARSE_ERROR".equalsIgnoreCase(response.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        // Errores funcionales de negocio (duplicados, validaciones QAD, GEN-O-2, etc.) -> HTTP 400
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}