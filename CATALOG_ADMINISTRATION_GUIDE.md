# Guía de Administración del Catálogo Multi-DataSource (ms-data-gateway)

## 1. Introducción y Arquitectura de Enrutamiento

`ms-data-gateway` está diseñado como una plataforma de datos agnóstica a la base de datos subyacente. Los clientes interactúan con un **único endpoint de consumo**:

```http
POST /api/v1/gateway/query
```

El microservicio realiza la resolución dinámica de la conexión en tiempo de ejecución:

```
                      POST /api/v1/gateway/query
                                  │
                                  ▼
                      gateway_query (Catálogo)
                                  │
                                  ▼
                    gateway_datasource (Catálogo)
                                  │
                                  ▼
                     DynamicDataSourceRouter
                                  │
                                  ▼
                           DatabaseDialect
                                  │
                                  ▼
                     Motor de BD Destino (JDBC)
                                  │
                                  ▼
                    Auditoría (gateway_query_execution)
                                  │
                                  ▼
                     Respuesta JSON Estandarizada
```

---

## 2. Endpoints Administrativos (Requieren `X-Admin-Key`)

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/v1/gateway/admin/datasources` | Registrar nuevo DataSource |
| `PUT` | `/api/v1/gateway/admin/datasources/{id}` | Actualizar metadatos del DataSource |
| `DELETE` | `/api/v1/gateway/admin/datasources/{id}` | Eliminar DataSource (bloqueado si tiene queries asociadas) |
| `GET` | `/api/v1/gateway/admin/datasources` | Listar todos los DataSources |
| `GET` | `/api/v1/gateway/admin/datasources/{id}` | Detalle de un DataSource específico |
| `POST` | `/api/v1/gateway/admin/queries` | Registrar nueva consulta SQL |
| `PUT` | `/api/v1/gateway/admin/queries/{code}` | Actualizar SQL, timeout o maxRows en caliente |
| `PATCH` | `/api/v1/gateway/admin/queries/{code}/toggle?enabled=false|true` | Habilitar / deshabilitar consulta sin redeploy |
| `DELETE` | `/api/v1/gateway/admin/queries/{code}` | Eliminar consulta del catálogo |
| `GET` | `/api/v1/gateway/admin/queries` | Listar consultas |
| `GET` | `/api/v1/gateway/admin/queries/{code}` | Detalle de consulta y sus parámetros |
| `GET` | `/api/v1/gateway/admin/queries/{code}/parameters` | Listar parámetros de una consulta |
| `POST` | `/api/v1/gateway/admin/queries/{code}/parameters` | Agregar parámetro formal a una consulta |
| `PUT` | `/api/v1/gateway/admin/queries/{code}/parameters/{name}` | Actualizar tipo, obligatoriedad o valor default |
| `DELETE` | `/api/v1/gateway/admin/queries/{code}/parameters/{name}` | Eliminar parámetro de una consulta |

---

## 3. Casos Prácticos de Integración Paso a Paso

### Caso A: Agregar una Segunda Base OpenEdge (`openedge-secondary`)

1. **Registro en `gateway_datasource` vía API:**
```bash
curl -X POST http://localhost:8080/api/v1/gateway/admin/datasources \
  -H "X-Admin-Key: dev-admin-key-999" \
  -H "Content-Type: application/json" \
  -d '{
    "datasourceId": "openedge-secondary",
    "datasourceCode": "OPENEDGE_SEC",
    "datasourceName": "OpenEdge Historical Database",
    "engineType": "OPENEDGE",
    "enabled": true,
    "defaultFlag": false,
    "description": "Base de datos secundaria Progress OpenEdge"
  }'
```

2. **Configuración en `application.yml`:**
```yaml
gateway:
  datasources:
    openedge-secondary:
      type: OPENEDGE
      driver-class-name: com.ddtek.jdbc.openedge.OpenEdgeDriver
      host: ${OPENEDGE_SEC_HOST:qvarrazldb02}
      port: ${OPENEDGE_SEC_PORT:18112}
      database: ${OPENEDGE_SEC_DATABASE:historydb}
      username: ${OPENEDGE_SEC_USER}
      password: ${OPENEDGE_SEC_PASSWORD}
      encryption-method: ssl
      crypto-protocol-version: TLSv1.2
      enable-cipher-suites: TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256
      validate-server-certificate: false
```

3. **Variables de Entorno:**
```powershell
$env:OPENEDGE_SEC_HOST = "qvarrazldb02"
$env:OPENEDGE_SEC_PORT = "18112"
$env:OPENEDGE_SEC_DATABASE = "historydb"
$env:OPENEDGE_SEC_USER = "sysprogress"
$env:OPENEDGE_SEC_PASSWORD = "secret_password"
```

4. **Registro de Consulta en `gateway_query`:**
```bash
curl -X POST http://localhost:8080/api/v1/gateway/admin/queries \
  -H "X-Admin-Key: dev-admin-key-999" \
  -H "Content-Type: application/json" \
  -d '{
    "queryCode": "GET_HISTORICAL_INVOICE",
    "queryName": "Facturas Históricas",
    "datasourceId": "openedge-secondary",
    "queryType": "SELECT",
    "sqlText": "SELECT inv_nbr, inv_date, inv_amt FROM pub.inv_hist WHERE inv_nbr = :invNum",
    "enabled": true,
    "timeoutSeconds": 30,
    "maxRows": 100
  }'
```

5. **Registro de Parámetro en `gateway_query_parameter`:**
```bash
curl -X POST http://localhost:8080/api/v1/gateway/admin/queries/GET_HISTORICAL_INVOICE/parameters \
  -H "X-Admin-Key: dev-admin-key-999" \
  -H "Content-Type: application/json" \
  -d '{
    "parameterName": "invNum",
    "parameterType": "STRING",
    "requiredFlag": true
  }'
```

6. **Ejecución desde el API Unificado:**
```bash
curl -X POST http://localhost:8080/api/v1/gateway/query \
  -H "X-API-Key: dev-api-key-001" \
  -H "Content-Type: application/json" \
  -d '{
    "queryCode": "GET_HISTORICAL_INVOICE",
    "params": { "invNum": "INV-2024-9988" }
  }'
```

---

### Caso B: Agregar una Segunda Base MySQL (`mysql-billing`)

1. **Registro en `gateway_datasource`:**
```json
{
  "datasourceId": "mysql-billing",
  "datasourceCode": "MYSQL_BILLING",
  "datasourceName": "MySQL Facturación",
  "engineType": "MYSQL",
  "enabled": true,
  "defaultFlag": false
}
```

2. **Configuración YAML:**
```yaml
gateway:
  datasources:
    mysql-billing:
      type: MYSQL
      driver-class-name: com.mysql.cj.jdbc.Driver
      jdbc-url: jdbc:mysql://${MYSQL_BILLING_HOST:localhost}:${MYSQL_BILLING_PORT:3306}/${MYSQL_BILLING_DB:billing}?useSSL=true&serverTimezone=UTC
      username: ${MYSQL_BILLING_USER}
      password: ${MYSQL_BILLING_PASSWORD}
```

3. **Variables de Entorno:**
`MYSQL_BILLING_HOST`, `MYSQL_BILLING_PORT`, `MYSQL_BILLING_DB`, `MYSQL_BILLING_USER`, `MYSQL_BILLING_PASSWORD`.

4. **Registro de Consulta:**
```json
{
  "queryCode": "BILLING_SUMMARY_BY_YEAR",
  "datasourceId": "mysql-billing",
  "queryType": "SELECT",
  "sqlText": "SELECT fiscal_year, SUM(total_amount) AS revenue FROM invoices WHERE fiscal_year = :year GROUP BY fiscal_year"
}
```

5. **Parámetro:**
`parameterName`: `year`, `parameterType`: `INTEGER`, `requiredFlag`: `true`.

6. **Ejecución:**
`POST /api/v1/gateway/query` con `{"queryCode": "BILLING_SUMMARY_BY_YEAR", "params": {"year": 2026}}`.

---

### Caso C: Agregar SQL Server (`SQLSERVER_ERP`)

1. **Registro en `gateway_datasource` (vía API Admin o SQL):**
```json
POST /api/v1/gateway/admin/datasources
Header: X-Admin-Key: dev-admin-key-999
{
  "datasourceId": "sqlserver-erp",
  "datasourceCode": "SQLSERVER_ERP",
  "datasourceName": "Microsoft SQL Server ERP Corporativo",
  "engineType": "SQLSERVER",
  "enabled": true,
  "defaultFlag": false,
  "description": "Base de datos transaccional ERP en SQL Server"
}
```

2. **Configuración YAML en `application-dev.yml` (o variables de entorno correspondientes):**
```yaml
gateway:
  datasources:
    sqlserver-erp:
      type: SQLSERVER
      driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
      jdbc-url: jdbc:sqlserver://${MSSQL_ERP_HOST:localhost}:${MSSQL_ERP_PORT:1433};databaseName=${MSSQL_ERP_DATABASE:erp_db};encrypt=true;trustServerCertificate=true;
      username: ${MSSQL_ERP_USER}
      password: ${MSSQL_ERP_PASSWORD}
```

3. **Variables de Entorno:**
`MSSQL_ERP_HOST`, `MSSQL_ERP_PORT`, `MSSQL_ERP_DATABASE`, `MSSQL_ERP_USER`, `MSSQL_ERP_PASSWORD`.

4. **Registro de Consulta en `gateway_query` (sin recompilar):**
```json
POST /api/v1/gateway/admin/queries
Header: X-Admin-Key: dev-admin-key-999
{
  "queryCode": "MSSQL_GET_ORDERS",
  "datasourceId": "sqlserver-erp",
  "queryType": "SELECT",
  "description": "Consultar órdenes por cliente en SQL Server",
  "sqlText": "SELECT OrderID, OrderDate, CustomerID FROM dbo.Orders WHERE CustomerID = :customerId",
  "enabled": true,
  "timeoutSeconds": 30,
  "maxRows": 500
}
```

---

### Caso D: Agregar PostgreSQL (`POSTGRES_CRM`)

1. **Registro en `gateway_datasource` (vía API Admin o SQL):**
```json
POST /api/v1/gateway/admin/datasources
Header: X-Admin-Key: dev-admin-key-999
{
  "datasourceId": "postgres-crm",
  "datasourceCode": "POSTGRES_CRM",
  "datasourceName": "PostgreSQL CRM Clientes",
  "engineType": "POSTGRESQL",
  "enabled": true,
  "defaultFlag": false,
  "description": "Base de datos de fidelización y clientes CRM en PostgreSQL"
}
```

2. **Configuración YAML en `application-dev.yml`:**
```yaml
gateway:
  datasources:
    postgres-crm:
      type: POSTGRESQL
      driver-class-name: org.postgresql.Driver
      jdbc-url: jdbc:postgresql://${PG_CRM_HOST:localhost}:${PG_CRM_PORT:5432}/${PG_CRM_DATABASE:crm_db}?sslmode=prefer
      username: ${PG_CRM_USER}
      password: ${PG_CRM_PASSWORD}
```

3. **Variables de Entorno:**
`PG_CRM_HOST`, `PG_CRM_PORT`, `PG_CRM_DATABASE`, `PG_CRM_USER`, `PG_CRM_PASSWORD`.

4. **Registro de Consulta en `gateway_query` (sin recompilar):**
```json
POST /api/v1/gateway/admin/queries
Header: X-Admin-Key: dev-admin-key-999
{
  "queryCode": "PG_LIST_CUSTOMERS",
  "datasourceId": "postgres-crm",
  "queryType": "SELECT",
  "description": "Obtener clientes activos con su correo en PostgreSQL",
  "sqlText": "SELECT customer_id, first_name, last_name, email FROM customers WHERE active = true",
  "enabled": true,
  "timeoutSeconds": 20,
  "maxRows": 1000
}
```

---

### Caso E: Agregar Oracle (`ORACLE_WMS`)

1. **Registro en `gateway_datasource` (vía API Admin o SQL):**
```json
POST /api/v1/gateway/admin/datasources
Header: X-Admin-Key: dev-admin-key-999
{
  "datasourceId": "oracle-wms",
  "datasourceCode": "ORACLE_WMS",
  "datasourceName": "Oracle Warehouse Management System (WMS)",
  "engineType": "ORACLE",
  "enabled": true,
  "defaultFlag": false,
  "description": "Sistema de gestión de almacenes e inventarios en Oracle Database"
}
```

2. **Configuración YAML en `application-dev.yml`:**
```yaml
gateway:
  datasources:
    oracle-wms:
      type: ORACLE
      driver-class-name: oracle.jdbc.OracleDriver
      jdbc-url: jdbc:oracle:thin:@//${ORACLE_WMS_HOST:localhost}:${ORACLE_WMS_PORT:1521}/${ORACLE_WMS_SERVICE:ORCLPDB1}
      username: ${ORACLE_WMS_USER}
      password: ${ORACLE_WMS_PASSWORD}
```

3. **Variables de Entorno:**
`ORACLE_WMS_HOST`, `ORACLE_WMS_PORT`, `ORACLE_WMS_SERVICE`, `ORACLE_WMS_USER`, `ORACLE_WMS_PASSWORD`.

4. **Registro de Consulta en `gateway_query` (sin recompilar):**
```json
POST /api/v1/gateway/admin/queries
Header: X-Admin-Key: dev-admin-key-999
{
  "queryCode": "ORACLE_STOCK_ITEMS",
  "datasourceId": "oracle-wms",
  "queryType": "SELECT",
  "description": "Consultar inventario de artículos por almacén en Oracle",
  "sqlText": "SELECT ITEM_CODE, WAREHOUSE_ID, ON_HAND_QTY FROM WMS_INVENTORY WHERE WAREHOUSE_ID = :warehouseId",
  "enabled": true,
  "timeoutSeconds": 30,
  "maxRows": 500
}
```

---

## 4. Auditoría y Trazabilidad

Todas las ejecuciones quedan registradas automáticamente en `gateway_query_execution`:
- `execution_id`: Secuencial único.
- `query_code`: Código de la consulta ejecutada.
- `datasource_id`: Motor de datos destino que resolvió la petición.
- `executed_by`: Usuario o aplicación cliente autenticada.
- `execution_time_ms`: Milisegundos empleados en la ejecución y corte.
- `rows_returned`: Número de filas entregadas.
- `success`: `1` para éxito, `0` para error.
- `error_message`: Detalle técnico de la falla en caso de error.
- `source_ip`: Dirección IP del consumidor.

---

## 5. Criterios de Diseño y Congelación de Arquitectura (Fase Actual)

> [!IMPORTANT]
> **ESTADO DE LA FASE: CONGELADA / PAUSADA**
> No se implementarán nuevas funcionalidades, dialectos ni motores adicionales hasta completar la validación funcional de conectividad y consultas contra el ambiente real de OpenEdge.

### Conclusiones Arquitectónicas Acordadas

1. **Catálogo de Control Centralizado:**
   - La base de datos de control y administración permanecerá en **MySQL** (`gateway-data`).
   - El routing multi-motor (OpenEdge, MySQL, PostgreSQL, SQL Server, Oracle) ya se encuentra desacoplado del código y empaquetado en el JAR.

2. **Alcance Exclusivo de JDBC:**
   - La conectividad JDBC directa hacia OpenEdge/QAD y otros motores se limitará a operaciones de lectura e información:
     - Consultas `SELECT` parametrizadas.
     - Metadatos (esquemas, tablas, columnas, índices).
     - Extracción para reportes, catálogos y dashboards analíticos.
     - Exploración administrativa controlada.

3. **Lógica de Negocio y Escrituras en QAD:**
   - **Las reglas y procesos de negocio de QAD NO serán reescritas en Java.**
   - Las operaciones de escritura (`INSERT`, `UPDATE`, `DELETE`) y transacciones de negocio complejas **no deben vulnerar las reglas del ERP ejecutando SQL directo contra las tablas de datos**.
   - Dichas operaciones deberán ejecutarse exclusivamente mediante los mecanismos nativos soportados por QAD/OpenEdge:
     - Programas Progress ABL (`.p` / `.r`).
     - AppServer / Progress OpenClient.
     - WebServices / APIs expuestas formalmente por QAD (REST, SOAP, WebSpeed).

4. **Hoja de Ruta Futura (Siguiente Fase):**
   - Una vez validado OpenEdge, el gateway evolucionará para soportar invocación de **servicios de negocio QAD** de forma unificada junto a las consultas JDBC.

### Pendientes Críticos para la Siguiente Fase

- [ ] Resolver y validar la conexión física real a Progress OpenEdge (host `qvarrazldb01`, puerto `18111`, BD `mfgdb`).
- [ ] Ejecutar exitosamente la consulta de validación:
  ```sql
  SELECT * FROM SYSPROGRESS.SYSTABLES
  ```
- [ ] Validar credenciales de servicio con el equipo de DBA / Infraestructura.
- [ ] Identificar cómo se encuentran expuestos los procesos `.p` en el ambiente QAD destino:
  - ¿Existe AppServer / PAS for OpenEdge?
  - ¿Servicios SOAP / REST de QAD?
  - ¿WebSpeed o conectores OpenClient?
  - ¿Servicio propietario intermedio?