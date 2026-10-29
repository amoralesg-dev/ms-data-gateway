# MS Data Gateway

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-6DB33F?style=for-the-badge&logo=spring)
![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?style=for-the-badge&logo=apache-maven)
![OpenEdge](https://img.shields.io/badge/Progress-OpenEdge-003399?style=for-the-badge)

Microservicio Spring Boot que expone información de bases de datos **Progress OpenEdge** (y futuras fuentes de datos) mediante APIs REST seguras. Implementa el patrón **Data Gateway**: ningún SQL llega desde el exterior — solo IDs de consultas registradas y parámetros tipados.

---

## 🏛️ Arquitectura

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Consumidores Externos                        │
│               (Otros microservicios, aplicaciones, etc.)            │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ HTTP REST + X-API-Key / X-Admin-Key
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      ms-data-gateway                                │
│                                                                     │
│  ┌────────────────┐   ┌──────────────────┐   ┌──────────────────┐   │
│  │  Security Layer │   │ Gateway API      │   │  Swagger/OpenAPI │   │
│  │  API/Admin Key  │   │ /gateway/query   │   │  /swagger-ui.html│   │
│  └────────┬───────┘   └────────┬─────────┘   └──────────────────┘   │
│           │                    │                                    │
│           └──────────┬─────────┘                                    │
│                      ▼                                              │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │            Catálogo Persistente (MySQL Control)            │    │
│  │   gateway_datasource │ gateway_query │ gateway_execution   │    │
│  └─────────────────────────────┬──────────────────────────────┘    │
│                                │                                    │
│  ┌─────────────────────────────▼──────────────────────────────┐    │
│  │        Enrutamiento Multi-Motor (DynamicDataSourceRouter)   │    │
│  │     OpenEdge │ MySQL │ PostgreSQL │ SQL Server │ Oracle    │    │
│  └─────────────────────────────┬──────────────────────────────┘    │
└────────────────────────────────┼────────────────────────────────────┘
                                 │
            ┌────────────────────┼────────────────────┐
            ▼                    ▼                    ▼
    ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
    │  OpenEdge /  │     │   MySQL      │     │  Postgres /  │
    │  QAD (JDBC)  │     │  Control     │     │  SQL Server  │
    │  [Lecturas]  │     │  [Catálogo]  │     │  / Oracle    │
    └──────────────┘     └──────────────┘     └──────────────┘

> [!NOTE]
> **Criterio de Diseño y Estado de la Fase:**  
> - **JDBC es exclusivo para lecturas**: `SELECT`, consultas parametrizadas, metadatos, reportes y dashboards.  
> - **Las reglas de negocio de QAD NO se reescriben en Java**: Operaciones de escritura y procesos transaccionales complejos deberán ejecutarse a través de los mecanismos nativos de QAD/OpenEdge (`.p`, `.r`, AppServer, WebServices, REST/SOAP de QAD).  
> - **Fase actual congelada**: No se implementarán más funcionalidades hasta validar la conexión física real a OpenEdge (`SELECT * FROM SYSPROGRESS.SYSTABLES`).
```

---

## 📋 Requisitos

| Componente | Versión |
|---|---|
| Java | 21+ |
| Maven | 3.8+ |
| Progress OpenEdge | 11.x / 12.x |
| Driver JDBC OpenEdge | `openedge.jar` |

---

## 🚀 Inicio Rápido

### 1. Colocar el driver JDBC de OpenEdge

El driver **no está en repositorios Maven públicos**. Debes obtenerlo manualmente:

```
# Desde la instalación de Progress OpenEdge (Windows):
C:\Progress\OpenEdge\java\openedge.jar

# Desde DBeaver: Help → Driver Manager → Progress OpenEdge → Libraries
```

Copia el JAR a la carpeta `libs/` del proyecto:
```
ms-data-gateway/
└── libs/
    └── openedge.jar   ← aquí
```

> El `pom.xml` referencia el JAR con `systemPath` apuntando a `libs/openedge.jar`.

### 2. Configurar la conexión

Edita `src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:datadirect:openedge://TU_HOST:3101;DatabaseName=TU_DATABASE
    username: TU_USUARIO
    password: TU_PASSWORD
```

O usa variables de entorno:
```bash
set DB_URL=jdbc:datadirect:openedge://localhost:3101;DatabaseName=sports2000
set DB_USER=sysprogress
set DB_PASSWORD=sysprogress
set API_KEY_1=mi-clave-segura-001
```

### 3. Ejecutar

```bash
# Con Maven
mvn spring-boot:run

# Con perfil específico
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Compilar y ejecutar JAR
mvn clean package -DskipTests
java -jar target/ms-data-gateway-1.0.0.jar --spring.profiles.active=dev
```

### 4. Verificar

```
http://localhost:8080/actuator/health    ← Health check (público)
http://localhost:8080/swagger-ui.html   ← Documentación interactiva
```

---

## 🔐 Seguridad

### Autenticación por API Key

Todas las rutas de negocio requieren el header `X-API-Key`:

```http
POST /api/v1/queries/execute
X-API-Key: dev-api-key-001
Content-Type: application/json
```

### Configuración de API Keys

En `application.yml`:
```yaml
gateway:
  security:
    api-key-header: X-API-Key
    api-keys:
      - ${API_KEY_1:dev-api-key-001}
      - ${API_KEY_2:dev-api-key-002}
```

### Endpoints públicos (sin autenticación)

| Endpoint | Descripción |
|---|---|
| `GET /actuator/health` | Health check |
| `GET /actuator/info` | Información de la app |
| `GET /swagger-ui.html` | UI de documentación |
| `GET /api-docs` | OpenAPI spec JSON |

### Protección contra SQL Injection

- ❌ **Ningún SQL se acepta desde la API** — el SQL vive en `application.yml`
- ✅ Los parámetros se pasan via `NamedParameterJdbcTemplate` (PreparedStatements)
- ✅ Solo consultas/operaciones registradas con un ID lógico pueden ejecutarse
- ✅ El tipo (`SELECT`/`INSERT`/`UPDATE`/`DELETE`) se valida internamente

---

## 📝 Registro de Consultas y Operaciones

Las consultas se registran en `application.yml` bajo `gateway.registered-queries`. **El SQL nunca llega desde el cliente.**

```yaml
gateway:
  registered-queries:

    # SELECT
    list-suppliers:
      sql: "SELECT ad_addr, ad_name FROM pub.addr WHERE ad_addr LIKE :prefix"
      description: "Listar proveedores por prefijo"
      type: SELECT

    # INSERT
    create-supplier:
      sql: "INSERT INTO pub.addr (ad_addr, ad_name) VALUES (:code, :name)"
      description: "Crear proveedor"
      type: INSERT

    # UPDATE
    update-supplier:
      sql: "UPDATE pub.addr SET ad_name = :name WHERE ad_addr = :code"
      description: "Actualizar proveedor"
      type: UPDATE

    # DELETE
    delete-supplier:
      sql: "DELETE FROM pub.addr WHERE ad_addr = :code"
      description: "Eliminar proveedor"
      type: DELETE
```

---

## 📡 Endpoints REST

### Consultas SELECT

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/queries/execute` | Ejecutar consulta registrada (con paginación) |
| `GET` | `/api/v1/queries/{queryId}` | Ejecutar consulta via query params |

**Request:**
```json
{
  "queryId": "list-suppliers",
  "params": { "prefix": "0001%" },
  "page": 1,
  "pageSize": 10
}
```

**Response:**
```json
{
  "success": true,
  "columns": [
    { "name": "ad_addr", "type": "VARCHAR", "jdbcType": 12, "columnSize": 8, "nullable": false },
    { "name": "ad_name", "type": "VARCHAR", "jdbcType": 12, "columnSize": 40, "nullable": true }
  ],
  "rows": [
    ["000001", "Proveedor A"],
    ["000002", "Proveedor B"]
  ],
  "totalRows": 2,
  "page": 1,
  "pageSize": 10,
  "totalPages": 1,
  "executionTimeMs": 45
}
```

### Operaciones de Escritura

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/operations/{operationId}` | Ejecutar INSERT/UPDATE/DELETE registrado |

**Request:**
```json
{
  "params": {
    "code": "000123",
    "name": "Proveedor Actualizado"
  }
}
```

**Response:**
```json
{
  "success": true,
  "rowsAffected": 1,
  "executionTimeMs": 23,
  "operationId": "update-supplier"
}
```

### Transacciones

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/transaction` | Ejecutar múltiples operaciones en una transacción |

**Request:**
```json
{
  "operations": [
    {
      "type": "INSERT",
      "queryId": "create-supplier",
      "params": { "code": "NEW01", "name": "Nuevo Proveedor", "city": "CDMX", "country": "MX" }
    },
    {
      "type": "UPDATE",
      "queryId": "update-supplier",
      "params": { "code": "NEW01", "name": "Proveedor Actualizado", "city": "Monterrey" }
    }
  ]
}
```

**En caso de fallo → rollback completo:**
```json
{
  "success": false,
  "failedOperationIndex": 1,
  "failedOperationId": "update-supplier",
  "errorCode": "TRANSACTION_OPERATION_FAILED",
  "message": "Unique constraint violation",
  "totalExecutionTimeMs": 67
}
```

### Metadatos

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/metadata/schemas` | Listar esquemas |
| `GET` | `/api/v1/metadata/tables` | Listar tablas |
| `GET` | `/api/v1/metadata/tables/{table}/columns` | Listar columnas |
| `GET` | `/api/v1/metadata/tables/{table}/primary-keys` | Claves primarias |
| `GET` | `/api/v1/metadata/tables/{table}/indexes` | Índices |
| `GET` | `/api/v1/metadata/tables/{table}/foreign-keys` | Foreign Keys |
| `GET` | `/api/v1/metadata/jdbc-types` | Tipos JDBC disponibles |
| `GET` | `/api/v1/metadata/database-info` | Info del servidor de BD |

### Registro

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/registry` | Listar todas las queries/ops registradas |
| `GET` | `/api/v1/registry/{queryId}` | Detalle de una query registrada |

---

## 📄 Respuesta de Error

```json
{
  "success": false,
  "errorCode": "QUERY_NOT_FOUND",
  "message": "Query not found: mi-query-inexistente"
}
```

**Códigos de error:**

| Código | Descripción |
|---|---|
| `QUERY_NOT_FOUND` | El queryId no existe en el registro |
| `INVALID_QUERY_TYPE` | Se intentó ejecutar un no-SELECT como query |
| `OPERATION_NOT_FOUND` | El operationId no existe |
| `INVALID_OPERATION_TYPE` | Se intentó ejecutar un SELECT como operación |
| `DELETE_DISABLED` | Las operaciones DELETE están deshabilitadas |
| `QUERY_EXECUTION_ERROR` | Error de JDBC al ejecutar la consulta |
| `OPERATION_EXECUTION_ERROR` | Error de JDBC al ejecutar la operación |
| `TRANSACTION_ERROR` | Error general de transacción |
| `TRANSACTION_OPERATION_FAILED` | Una operación dentro de la transacción falló |
| `VALIDATION_ERROR` | Error de validación del request |
| `SQL_ERROR_*` | Error SQL de la base de datos (con código JDBC) |

---

## 📊 Paginación

```json
{
  "queryId": "list-suppliers",
  "params": { "prefix": "%" },
  "page": 2,
  "pageSize": 25
}
```

> **Nota OpenEdge:** La paginación se implementa en memoria (Java) ya que OpenEdge puede no soportar `OFFSET/FETCH NEXT` nativo. El límite máximo de filas (`gateway.queries.max-rows`) protege la memoria del servidor.

---

## ⚙️ Configuración Completa

| Propiedad | Descripción | Default |
|---|---|---|
| `gateway.security.api-key-header` | Header HTTP para la API Key | `X-API-Key` |
| `gateway.security.api-keys` | Lista de API Keys válidas | - |
| `gateway.queries.max-rows` | Máximo de registros por consulta | `1000` |
| `gateway.queries.default-page-size` | Registros por página por defecto | `50` |
| `gateway.operations.delete-enabled` | Habilitar operaciones DELETE | `true` |
| `gateway.operations.audit-enabled` | Habilitar logs de auditoría | `true` |

---

## 🌍 Perfiles de Spring

| Perfil | Descripción |
|---|---|
| `dev` | Desarrollo local — logs DEBUG, credenciales locales |
| `prod` | Producción — logs WARN, credenciales via ENV vars |

```bash
# Activar perfil
java -jar ms-data-gateway.jar --spring.profiles.active=prod
```

---

## 📈 Observabilidad

### Actuator
```
GET /actuator/health     → Estado del servicio y conexión BD
GET /actuator/info       → Información de la app
GET /actuator/metrics    → Métricas disponibles
GET /actuator/prometheus → Formato Prometheus para scraping
```

### Métricas Personalizadas

| Métrica | Tipo | Tags |
|---|---|---|
| `gateway.query.execution` | Timer | `queryId` |
| `gateway.query.count` | Counter | `queryId`, `status` |
| `gateway.operation.execution` | Timer | `operationId`, `type` |
| `gateway.operation.count` | Counter | `operationId`, `type`, `status` |

### HikariCP (incluido automáticamente)
```
hikaricp.connections.active
hikaricp.connections.idle
hikaricp.connections.pending
hikaricp.connections.timeout.total
```

---

## 🧪 Pruebas

```bash
# Ejecutar todas las pruebas
mvn test

# Solo pruebas unitarias
mvn test -Dtest="DataGatewayServiceTest,QueryControllerTest"

# Prueba de integración (Spring Context)
mvn test -Dtest="MsDataGatewayApplicationTests"
```

> Las pruebas usan **H2 in-memory** como reemplazo de OpenEdge — no requieren conexión real.

---

## 📮 Postman

La colección Postman está en:
```
docs/ms-data-gateway.postman_collection.json
```

**Importar en Postman:**
1. File → Import → Upload Files
2. Seleccionar `ms-data-gateway.postman_collection.json`
3. La variable `baseUrl` apunta a `http://localhost:8080`
4. La variable `apiKey` usa `dev-api-key-001`

**Carpetas incluidas:**
- 🔍 1 - Queries (SELECT)
- ✏️ 2 - Operations (INSERT, UPDATE, DELETE)
- 🔄 3 - Transactions
- 📊 4 - Metadata
- 📋 5 - Registry
- ❤️ 6 - Actuator & Health
- 🔒 7 - Security Tests (incluyendo prueba de SQL Injection)
- 📖 8 - Swagger

---

## 🔮 Extensibilidad — Agregar nuevas fuentes de datos

El `DataGatewayService` está desacoplado de OpenEdge. Para agregar SQL Server:

1. Agregar el datasource en `application.yml` con el perfil correspondiente
2. Crear un `SqlServerDataGatewayService` que implemente la misma interfaz
3. Los consumidores siguen recibiendo el mismo JSON estándar

Ver `docs/architecture.md` para el diseño completo de extensión.

---

## 📁 Estructura del Proyecto

```
ms-data-gateway/
├── docs/
│   ├── architecture.md                         ← Arquitectura detallada
│   └── ms-data-gateway.postman_collection.json ← Colección Postman
├── libs/
│   ├── README.md                               ← Instrucciones del driver
│   └── openedge.jar                            ← Colocar aquí el driver
├── src/
│   ├── main/
│   │   ├── java/com/gateway/
│   │   │   ├── MsDataGatewayApplication.java
│   │   │   ├── config/
│   │   │   │   ├── GatewayProperties.java      ← @ConfigurationProperties
│   │   │   │   ├── OpenApiConfig.java          ← Swagger config
│   │   │   │   └── SecurityConfig.java         ← Spring Security
│   │   │   ├── controller/
│   │   │   │   ├── QueryController.java        ← /api/v1/queries
│   │   │   │   ├── OperationController.java    ← /api/v1/operations
│   │   │   │   ├── TransactionController.java  ← /api/v1/transaction
│   │   │   │   ├── MetadataController.java     ← /api/v1/metadata
│   │   │   │   └── RegistryController.java     ← /api/v1/registry
│   │   │   ├── dto/
│   │   │   │   ├── QueryRequest.java
│   │   │   │   ├── OperationRequest.java
│   │   │   │   └── TransactionRequest.java
│   │   │   ├── exception/
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── model/
│   │   │   │   ├── QueryResult.java            ← Respuesta estándar SELECT
│   │   │   │   ├── WriteResult.java            ← Respuesta estándar escritura
│   │   │   │   ├── TransactionResult.java
│   │   │   │   ├── ColumnInfo.java
│   │   │   │   ├── TableInfo.java
│   │   │   │   ├── SchemaInfo.java
│   │   │   │   ├── PrimaryKeyInfo.java
│   │   │   │   ├── IndexInfo.java
│   │   │   │   └── ForeignKeyInfo.java
│   │   │   ├── security/
│   │   │   │   └── ApiKeyAuthFilter.java
│   │   │   └── service/
│   │   │       ├── DataGatewayService.java     ← Lógica principal
│   │   │       └── MetadataService.java        ← Metadatos JDBC
│   │   └── resources/
│   │       ├── application.yml                 ← Config base + queries registradas
│   │       ├── application-dev.yml             ← Perfil desarrollo
│   │       └── application-prod.yml            ← Perfil producción
│   └── test/
│       └── java/com/gateway/
│           ├── MsDataGatewayApplicationTests.java
│           ├── controller/
│           │   └── QueryControllerTest.java
│           └── service/
│               └── DataGatewayServiceTest.java
├── libs/
│   └── openedge.jar  ← Colocar driver aquí
├── pom.xml
└── README.md
```

---

## 📜 Licencia

Uso interno. Consultar con el equipo de arquitectura antes de distribuir.
