# Guía de Extensión para Servicios de Negocio QAD (QXtend SOAP)

Esta guía describe el procedimiento estándar para incorporar **cualquier nuevo servicio de negocio de QAD** (por ejemplo: `BCustomer`, `BBusinessRelation`, `BItem`, `BAddress`, `BPurchaseOrder`, etc.) dentro de `ms-data-gateway` sin modificar el núcleo del motor `QadSoapEngineService`.

---

## Principios de Arquitectura

1. **Abstracción Total para Clientes REST**:
   - Los consumidores de la API **NUNCA** conocen ni manipulan XML, SOAP Envelopes, WSDLs, WS-Addressing ni `dsSessionContext`.
   - El cliente envía y recibe **JSON limpio y estándar**.
2. **Desacoplamiento e Inversión de Control**:
   - `QadSoapEngineService` es 100% genérico.
   - La resolución de servicios se basa en el registro `QadServiceRegistry`, que auto-detecta cualquier Bean que implemente `QadBusinessMapper<T>`.
3. **Control y Auditoría Centralizada**:
   - Cada ejecución queda auditada en la tabla `gateway_service_execution` en MySQL (`gateway-data`).
   - El endpoint y habilitación pueden controlarse dinámicamente desde `gateway_service`.

---

## Flujo de 5 Pasos para Agregar un Nuevo Servicio QAD

Todo nuevo servicio QAD que se incorpore a la plataforma debe incluir de forma obligatoria los siguientes artefactos:

1. **Request DTO tipado**:
   - Crear la clase en `com.gateway.qad.dto` con sus validaciones Bean Validation (`@NotBlank`, `@Positive`, etc.) y anotaciones OpenAPI Swagger (`@Schema`).
2. **Mapper de Negocio (`QadBusinessMapper<T>`)**:
   - Implementar la interfaz en `com.gateway.qad.mapper.impl` anotada con `@Component`.
   - Utilizar serialización DOM W3C mediante `XmlDocumentHelper` para construir el XML interno del servicio sin concatenación manual de cadenas.
3. **Registro en Catálogo MySQL (`gateway_service`)**:
   - Registrar el servicio con su `service_code`, `service_name`, `mapper_class`, `operation_name`, `endpoint_url`, `timeout_seconds` y estado `enabled`.
4. **Endpoint REST en Controller**:
   - Exponer el endpoint de conveniencia en `QadBusinessServiceController` invocando `qadSoapEngineService.executeService(SERVICE_CODE, request, user, ip)`.
5. **Sincronización de Colección Postman (`docs/postman/ms-data-gateway.postman_collection.json`)**:
   - Agregar el Request en la carpeta `"1 - QAD Business Services (SOAP QXtend)"`.
   - Incluir ejemplos guardados (Responses):
     - **Caso de Éxito (HTTP 200 OK)** con payload de respuesta típico.
     - **Caso de Error Funcional de Negocio (HTTP 400 Bad Request)** con excepciones `dsExceptions` / `temp_err_msg`.
     - **Caso de Error Técnico / SOAP Fault (HTTP 500 Internal Server Error)**.

---

## Manejo Unificado de Errores

| Tipo de Error | Condición QAD detectada | Código HTTP | Estructura Response |
| :--- | :--- | :--- | :--- |
| **Éxito** | `<result>success</result>` | 200 OK | `success: true`, `data`, `sessionContext`, `executionTimeMs` |
| **Error Funcional** | `<result>error</result>` o presencia de `<dsExceptions>` | 400 Bad Request | `success: false`, `errorCode`, `message`, `exceptions: [...]` |
| **SOAP Fault** | `<soapenv:Fault>` / `<faultstring>` | 500 Internal Server Error | `success: false`, `errorCode: "SOAP_FAULT"`, `message` |
| **Servicio Deshabilitado** | `enabled = 0` en catálogo | 500 Internal Server Error | `success: false`, `errorCode: "SERVICE_DISABLED"` |
| **Falla de Red / Timeout** | Excepción de conexión HTTP con QXtend | 500 Internal Server Error | `success: false`, `errorCode: "EXECUTION_EXCEPTION"` |