package com.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de conexión a Progress OpenEdge via JDBC DataDirect.
 *
 * <p>Mapea el bloque {@code openedge:} en application.yml.
 * La URL JDBC se construye dinámicamente en {@link OpenEdgeDataSourceConfig}
 * combinando estas propiedades con los parámetros SSL requeridos por el servidor.
 *
 * <p><b>URL validada en DBeaver:</b>
 * <pre>
 * jdbc:datadirect:openedge://qvarrazldb01:18111;
 *   databaseName=mfgdb;
 *   User=&lt;USER&gt;;Password=&lt;PASSWORD&gt;;
 *   encryptionMethod=ssl;
 *   CryptoProtocolVersion=TLSv1.2;
 *   EnableCipherSuites=TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256;
 *   validateServerCertificate=false
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "openedge")
public class OpenEdgeProperties {

    /** Host del servidor OpenEdge. Variable de entorno: OPENEDGE_HOST */
    private String host;

    /** Puerto AppServer del servidor OpenEdge. Variable de entorno: OPENEDGE_PORT */
    private int port = 18111;

    /** Nombre de la base de datos OpenEdge. Variable de entorno: OPENEDGE_DATABASE */
    private String database;

    /** Usuario de conexión. Variable de entorno: OPENEDGE_USER — NUNCA en código fuente */
    private String username;

    /** Contraseña de conexión. Variable de entorno: OPENEDGE_PASSWORD — NUNCA en código fuente */
    private String password;

    // ── Parámetros SSL/TLS ──────────────────────────────────────────────────

    /**
     * Método de encriptación. Valores soportados: {@code ssl}, {@code requestSSL}, {@code noEncryption}.
     * El servidor OpenEdge requiere {@code ssl}.
     */
    private String encryptionMethod = "ssl";

    /**
     * Versión del protocolo TLS. El servidor requiere {@code TLSv1.2}.
     * Mapea al parámetro JDBC: {@code CryptoProtocolVersion}
     */
    private String cryptoProtocolVersion = "TLSv1.2";

    /**
     * Cipher suites habilitados para la conexión SSL.
     * Mapea al parámetro JDBC: {@code EnableCipherSuites}
     * Valor por defecto validado en DBeaver.
     */
    private String enableCipherSuites =
            "TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256";

    /**
     * Si {@code false}, no valida el certificado del servidor SSL.
     * Útil en ambientes con certificados auto-firmados.
     * Mapea al parámetro JDBC: {@code validateServerCertificate}
     */
    private boolean validateServerCertificate = false;

    // ── HikariCP Pool ───────────────────────────────────────────────────────

    /** Configuración del pool HikariCP */
    private Hikari hikari = new Hikari();

    @Data
    public static class Hikari {
        private String poolName = "OpenEdgePool";
        private int minimumIdle = 2;
        private int maximumPoolSize = 10;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
        private long leakDetectionThreshold = 60000;
        /**
         * Query de validación de conexión para OpenEdge.
         * Se consulta al schema del sistema (sysprogress) que siempre debe existir.
         */
        private String connectionTestQuery =
                "SELECT 1 FROM sysprogress.SYSTABLES WHERE TBLNAME = 'SYSTABLES'";
    }

    /**
     * Construye la URL JDBC completa para el driver DataDirect OpenEdge.
     *
     * <p>Formato: {@code jdbc:datadirect:openedge://host:port;param1=val1;param2=val2;...}
     *
     * <p><b>IMPORTANTE:</b> User y Password NO se incluyen en la URL — se pasan
     * al DataSource mediante {@link com.zaxxer.hikari.HikariConfig#setUsername(String)}
     * y {@link com.zaxxer.hikari.HikariConfig#setPassword(String)} para evitar
     * que aparezcan en logs de configuración.
     *
     * @return URL JDBC completa con parámetros SSL
     */
    public String buildJdbcUrl() {
        if ("NoEncryption".equalsIgnoreCase(encryptionMethod)) {
            return String.format(
                    "jdbc:datadirect:openedge://%s:%d;databaseName=%s;encryptionMethod=NoEncryption",
                    host, port, database
            );
        }
        return String.format(
                "jdbc:datadirect:openedge://%s:%d;databaseName=%s;" +
                "encryptionMethod=%s;" +
                "CryptoProtocolVersion=%s;" +
                "EnableCipherSuites=%s;" +
                "validateServerCertificate=%s",
                host, port, database,
                encryptionMethod,
                cryptoProtocolVersion,
                enableCipherSuites,
                validateServerCertificate
        );
    }
}
