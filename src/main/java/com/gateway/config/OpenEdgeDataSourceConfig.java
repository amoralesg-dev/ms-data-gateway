package com.gateway.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuración manual del DataSource para Progress OpenEdge via JDBC DataDirect.
 *
 * <h3>¿Por qué configuración manual en lugar de Spring Boot autoconfigure?</h3>
 * <p>Spring Boot autoconfigura el DataSource desde {@code spring.datasource.*}, lo que
 * funciona para bases de datos convencionales. Sin embargo, OpenEdge requiere parámetros
 * SSL específicos en la URL JDBC ({@code encryptionMethod}, {@code CryptoProtocolVersion},
 * {@code EnableCipherSuites}) que deben quedar codificados directamente en la URL de
 * conexión. Manejarlos desde el YAML de forma segura y componible requiere construir
 * la URL programáticamente.
 *
 * <h3>Driver JDBC utilizado</h3>
 * <ul>
 *   <li>{@code openedge.jar} — DataDirect JDBC Driver para OpenEdge
 *       <ul>
 *         <li>Clase: {@code com.ddtek.jdbc.openedge.OpenEdgeDriver}</li>
 *         <li>Registrado via {@code META-INF/services/java.sql.Driver} (ServiceLoader)</li>
 *         <li>Incluye {@code com.ddtek.jdbcx.openedge.OpenEdgeDataSource}</li>
 *       </ul>
 *   </li>
 *   <li>{@code progress.jar} — Librería base de Progress/OpenEdge
 *       <ul>
 *         <li>Provee clases SSL: {@code com.progress.ubroker.ssl}, {@code com.progress.mf.runtime.SSLConnectionConfiguration}</li>
 *         <li><strong>REQUERIDA</strong> para conexiones con {@code encryptionMethod=ssl}</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>URL JDBC validada desde DBeaver</h3>
 * <pre>
 * jdbc:datadirect:openedge://qvarrazldb01:18111;
 *   databaseName=mfgdb;
 *   encryptionMethod=ssl;
 *   CryptoProtocolVersion=TLSv1.2;
 *   EnableCipherSuites=TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256;
 *   validateServerCertificate=false
 * </pre>
 * <p>Nota: {@code User} y {@code Password} se pasan a HikariCP separadamente para
 * evitar que las credenciales aparezcan en logs de configuración o stack traces.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${OPENEDGE_HOST:}') and T(org.springframework.util.StringUtils).hasText('${OPENEDGE_USER:}')")
public class OpenEdgeDataSourceConfig {

    /**
     * Nombre de la clase del driver JDBC DataDirect para OpenEdge.
     * Aunque el driver usa ServiceLoader (automático en Java 6+),
     * se registra explícitamente en HikariCP para mayor claridad y compatibilidad.
     */
    private static final String OPENEDGE_DRIVER_CLASS = "com.ddtek.jdbc.openedge.OpenEdgeDriver";

    private final OpenEdgeProperties openEdgeProperties;

    /**
     * DataSource HikariCP configurado para Progress OpenEdge con SSL/TLS.
     *
     * <p><b>Parámetros de conexión:</b>
     * <ul>
     *   <li>La URL JDBC se construye via {@link OpenEdgeProperties#buildJdbcUrl()}</li>
     *   <li>Credenciales se pasan directamente a HikariCP (NO en la URL)</li>
     *   <li>El pool valida conexiones con una query al schema {@code sysprogress}</li>
     * </ul>
     *
     * @return DataSource configurado y listo para uso
     */
    @Bean(name = "openEdgeDataSource")
    public DataSource openEdgeDataSource() {
        String jdbcUrl = openEdgeProperties.buildJdbcUrl();

        // Loguear la URL sin credenciales para facilitar el diagnóstico
        log.info("Configurando DataSource OpenEdge:");
        log.info("  URL JDBC: {}", jdbcUrl);
        log.info("  Host: {}:{}", openEdgeProperties.getHost(), openEdgeProperties.getPort());
        log.info("  Base de datos: {}", openEdgeProperties.getDatabase());
        log.info("  Usuario: {}", openEdgeProperties.getUsername());
        log.info("  SSL: encryptionMethod={}, CryptoProtocolVersion={}",
                openEdgeProperties.getEncryptionMethod(),
                openEdgeProperties.getCryptoProtocolVersion());
        log.info("  Ciphers: {}", openEdgeProperties.getEnableCipherSuites());
        log.info("  validateServerCertificate: {}", openEdgeProperties.isValidateServerCertificate());

        OpenEdgeProperties.Hikari hikariProps = openEdgeProperties.getHikari();

        HikariConfig config = new HikariConfig();

        // Driver
        config.setDriverClassName(OPENEDGE_DRIVER_CLASS);

        // URL con parámetros SSL (sin credenciales)
        config.setJdbcUrl(jdbcUrl);

        // Credenciales separadas — HikariCP las pasa al driver de forma segura
        config.setUsername(openEdgeProperties.getUsername());
        config.setPassword(openEdgeProperties.getPassword());

        // Pool settings
        config.setPoolName(hikariProps.getPoolName());
        config.setMinimumIdle(hikariProps.getMinimumIdle());
        config.setMaximumPoolSize(hikariProps.getMaximumPoolSize());
        config.setConnectionTimeout(hikariProps.getConnectionTimeout());
        config.setIdleTimeout(hikariProps.getIdleTimeout());
        config.setMaxLifetime(hikariProps.getMaxLifetime());
        config.setLeakDetectionThreshold(hikariProps.getLeakDetectionThreshold());
        config.setInitializationFailTimeout(-1);

        // Validación de conexión — query al schema de sistema de OpenEdge
        config.setConnectionTestQuery(hikariProps.getConnectionTestQuery());

        // Propiedades adicionales del driver DataDirect
        config.addDataSourceProperty("encryptionMethod", openEdgeProperties.getEncryptionMethod());
        if (!"NoEncryption".equalsIgnoreCase(openEdgeProperties.getEncryptionMethod())) {
            config.addDataSourceProperty("CryptoProtocolVersion", openEdgeProperties.getCryptoProtocolVersion());
            config.addDataSourceProperty("EnableCipherSuites", openEdgeProperties.getEnableCipherSuites());
            config.addDataSourceProperty("validateServerCertificate",
                    String.valueOf(openEdgeProperties.isValidateServerCertificate()));
        }

        if (openEdgeProperties.getUsername() != null && !openEdgeProperties.getUsername().isBlank()) {
            config.addDataSourceProperty("user", openEdgeProperties.getUsername());
        }
        if (openEdgeProperties.getPassword() != null && !openEdgeProperties.getPassword().isBlank()) {
            config.addDataSourceProperty("password", openEdgeProperties.getPassword());
        }

        // Optimizaciones generales para JDBC
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        HikariDataSource dataSource = new HikariDataSource(config);
        log.info("DataSource OpenEdge inicializado correctamente. Pool: '{}'", hikariProps.getPoolName());
        return dataSource;
    }

    @Bean(name = "openEdgeJdbcTemplate")
    public JdbcTemplate openEdgeJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("openEdgeDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "openEdgeNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate openEdgeNamedParameterJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("openEdgeDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
