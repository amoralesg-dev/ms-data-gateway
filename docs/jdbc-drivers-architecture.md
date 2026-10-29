# Arquitectura de Drivers JDBC — MS Data Gateway

## Análisis de `java.zip`

El archivo `java.zip` incluido en el proyecto contiene los drivers JDBC oficiales de Progress/DataDirect para OpenEdge. Estos drivers **no están disponibles en ningún repositorio Maven público**.

### Contenido del ZIP

| Archivo | Tamaño | Rol |
|---|---|---|
| `openedge.jar` | ~1.7 MB | **Driver JDBC principal** |
| `progress.jar` | ~5.5 MB | **Librería base SSL/TLS** |

---

## openedge.jar — Driver JDBC DataDirect

### Identificación

| Propiedad | Valor |
|---|---|
| Clase del driver | `com.ddtek.jdbc.openedge.OpenEdgeDriver` |
| Registro ServiceLoader | `META-INF/services/java.sql.Driver` |
| Creado con | Apache Ant 1.8.2, JDK 1.6.0 |
| Total de clases | 449 |

### Packages incluidos

```
com.ddtek.jdbc.openedge.*          ← Driver principal (OpenEdgeDriver, conexiones)
com.ddtek.jdbc.openedge.client.*   ← Comunicación de red con el servidor
com.ddtek.jdbc.extensions.*        ← ExtDataSource, ExtPooledConnection
com.ddtek.jdbcx.openedge.*         ← OpenEdgeDataSource, OpenEdgeDataSource40 ← HikariCP compatible
com.ddtek.jdbcx.openedgebase.*     ← BaseDataSource
com.ddtek.jdbcsnoopopenedge.*      ← Debug/snoop
com.ddtek.jdbcspyopenedge.*        ← SpyLogger para DataSource
com.ddtek.openedgeutil.*           ← Utilidades internas
com.ddtek.util.*                   ← Utilidades genéricas
```

### Compatibilidad con HikariCP

`openedge.jar` incluye `OpenEdgeDataSource40` que implementa `javax.sql.DataSource`, haciendo el driver directamente compatible con HikariCP vía `setDriverClassName`.

---

## progress.jar — Librería Base de Progress

### Identificación

| Propiedad | Valor |
|---|---|
| Creado con | Apache Ant 1.9.6, JDK 1.8.0 |
| Total de clases | 2,767 |
| Dependencias declaradas | `messages.jar, schema.jar, ext/sslj.jar, ext/certj.jar, ext/cryptoj.jar` |

> **Nota:** Las dependencias declaradas en el `MANIFEST.MF` (`sslj.jar`, `certj.jar`, `cryptoj.jar`) son de SafeNet/Entrust y se utilizan para la criptografía SSL a nivel de Progress. En la práctica, con el driver DataDirect (`openedge.jar`) y el flag `encryptionMethod=ssl`, Java utiliza su propia implementación SSL y estas librerías externas raramente son necesarias.

### Packages relevantes para SSL

```
com.progress.ubroker.ssl.*                    ← Clases SSL del UBroker
com.progress.mf.runtime.SSLConnectionConfig  ← Configuración de conexión SSL
com.progress.chimera.*                        ← Admin/plugin classloading
com.progress.system.*                         ← Utilidades del sistema Progress
```

### ¿Por qué es OBLIGATORIA para SSL?

El driver `openedge.jar` carga clases de `progress.jar` en tiempo de ejecución para configurar el contexto SSL de la conexión. Sin `progress.jar` en el classpath, la apertura de conexiones SSL (`encryptionMethod=ssl`) falla con:

```
java.lang.ClassNotFoundException: com.progress.ubroker.ssl.SomeClass
```

---

## Estrategia de Distribución — Repositorio Maven Local

### ¿Por qué repositorio local y no `systemPath`?

| Estrategia | Ventajas | Desventajas |
|---|---|---|
| `systemPath` en POM | Simple | ❌ No se incluye en fat JAR automáticamente, path absoluto frágil, advertencias Maven |
| Repositorio Maven local | ✅ Fat JAR correcto, sin advertencias, mismo flujo que deps públicas | Requiere `mvn install:install-file` una vez por máquina |
| Repositorio Maven privado (Nexus/Artifactory) | ✅ Ideal para equipos | Requiere infraestructura |

**Decisión: Repositorio Maven local** — Los JARs se instalan automáticamente con `install-drivers.ps1` y quedan en `~/.m2`, por lo que el proceso de build es idéntico al de cualquier dependencia pública.

### Coordenadas Maven asignadas

```xml
<!-- Driver JDBC principal -->
<dependency>
    <groupId>com.progress.datadirect</groupId>
    <artifactId>openedge</artifactId>
    <version>6.0.0</version>
</dependency>

<!-- Librería base SSL/TLS -->
<dependency>
    <groupId>com.progress</groupId>
    <artifactId>progress</artifactId>
    <version>12.0.0</version>
</dependency>
```

> La versión `6.0.0` y `12.0.0` son versiones internas asignadas al momento de la instalación. El JAR original no contiene información de versión en su MANIFEST.

---

## Construcción del DataSource

### Flujo completo

```
application.yml (openedge.*)
        ↓
OpenEdgeProperties (@ConfigurationProperties)
  ├── host, port, database
  ├── username, password
  ├── encryptionMethod=ssl
  ├── cryptoProtocolVersion=TLSv1.2
  ├── enableCipherSuites=TLS_RSA_WITH_AES_128_CBC_SHA256,...
  └── validateServerCertificate=false
        ↓
OpenEdgeDataSourceConfig (@Configuration)
  ├── openEdgeProperties.buildJdbcUrl()
  │     → "jdbc:datadirect:openedge://host:port;databaseName=db;
  │        encryptionMethod=ssl;CryptoProtocolVersion=TLSv1.2;
  │        EnableCipherSuites=...;validateServerCertificate=false"
  ├── HikariConfig.setJdbcUrl(url)       ← URL sin credenciales
  ├── HikariConfig.setUsername(user)     ← Separado para evitar logs
  ├── HikariConfig.setPassword(pass)     ← Separado para evitar logs
  └── HikariConfig.addDataSourceProperty("encryptionMethod", "ssl")
        ↓
HikariDataSource (bean: DataSource)
  └── com.ddtek.jdbc.openedge.OpenEdgeDriver
        ↓
JdbcTemplate + NamedParameterJdbcTemplate (beans)
        ↓
DataGatewayService / MetadataService
```

### Mapeo de parámetros JDBC → YAML

| Parámetro JDBC (URL) | Propiedad YAML | Variable de entorno |
|---|---|---|
| `//host:port` | `openedge.host` + `openedge.port` | `OPENEDGE_HOST`, `OPENEDGE_PORT` |
| `databaseName=X` | `openedge.database` | `OPENEDGE_DATABASE` |
| `User=X` (NO en URL) | `openedge.username` | `OPENEDGE_USER` |
| `Password=X` (NO en URL) | `openedge.password` | `OPENEDGE_PASSWORD` |
| `encryptionMethod=ssl` | `openedge.encryption-method` | — |
| `CryptoProtocolVersion=TLSv1.2` | `openedge.crypto-protocol-version` | — |
| `EnableCipherSuites=TLS_...` | `openedge.enable-cipher-suites` | — |
| `validateServerCertificate=false` | `openedge.validate-server-certificate` | — |

### ¿Por qué User/Password NO van en la URL?

1. **Seguridad**: La URL JDBC puede aparecer en logs, stack traces, dumps de configuración de Spring Boot Actuator (`/actuator/env`). Pasar credenciales en la URL las expone en texto plano.
2. **HikariCP**: Gestiona las credenciales de forma segura cuando se pasan vía `setUsername()`/`setPassword()`.
3. **Rotación de credenciales**: Facilita cambiar credenciales sin modificar la URL de conexión.

---

## URL JDBC Validada — Referencia Completa

La URL validada desde DBeaver con todos los parámetros:

```
jdbc:datadirect:openedge://qvarrazldb01:18111;
  databaseName=mfgdb;
  User=<USER>;
  Password=<PASSWORD>;
  encryptionMethod=ssl;
  CryptoProtocolVersion=TLSv1.2;
  EnableCipherSuites=TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256;
  validateServerCertificate=false
```

La URL construida programáticamente por `OpenEdgeProperties.buildJdbcUrl()` (sin credenciales):

```
jdbc:datadirect:openedge://qvarrazldb01:18111;databaseName=mfgdb;encryptionMethod=ssl;CryptoProtocolVersion=TLSv1.2;EnableCipherSuites=TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256;validateServerCertificate=false
```

Las credenciales se pasan separadamente a HikariCP.

---

## Guía para Nuevo Desarrollador

```bash
# 1. Clonar/obtener el proyecto (java.zip ya está incluido)
cd ms-data-gateway

# 2. Instalar drivers en .m2 (solo la primera vez en cada máquina)
.\install-drivers.ps1

# 3. Configurar credenciales (NUNCA hardcodear en archivos)
$env:OPENEDGE_USER = "mi_usuario"
$env:OPENEDGE_PASSWORD = "mi_password"

# 4. Opcional: override de host/db si se conecta a otro ambiente
$env:OPENEDGE_HOST = "mi-servidor-oe"
$env:OPENEDGE_PORT = "18111"
$env:OPENEDGE_DATABASE = "mfgdb"

# 5. Compilar y ejecutar
mvn clean compile
mvn spring-boot:run

# 6. Verificar conexión
curl http://localhost:8080/actuator/health
```

---

## Consideraciones Futuras — Nexus/Artifactory

Para equipos más grandes, se recomienda publicar los drivers en un repositorio Maven privado:

```xml
<!-- En pom.xml -->
<repositories>
    <repository>
        <id>company-nexus</id>
        <url>https://nexus.company.com/repository/maven-releases/</url>
    </repository>
</repositories>
```

Esto elimina la necesidad de `install-drivers.ps1` y permite compilación en CI/CD sin pasos adicionales.
