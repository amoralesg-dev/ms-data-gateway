package com.gateway.service;

import com.gateway.config.GatewayProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor dinámico de enrutamiento y caché de DataSources.
 * Permite resolver JdbcTemplate y NamedParameterJdbcTemplate para cualquier
 * datasourceId registrado en la configuración o catálogo.
 */
@Slf4j
@Service
public class DynamicDataSourceRouter {

    private final DataSource catalogDataSource;
    private final GatewayProperties gatewayProperties;
    private final DataSource openEdgeDataSource;

    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, NamedParameterJdbcTemplate> namedJdbcTemplateMap = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplateMap = new ConcurrentHashMap<>();

    public DynamicDataSourceRouter(@Qualifier("catalogDataSource") DataSource catalogDataSource,
                                   GatewayProperties gatewayProperties,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   @Qualifier("openEdgeDataSource") DataSource openEdgeDataSource) {
        this.catalogDataSource = catalogDataSource;
        this.gatewayProperties = gatewayProperties;
        this.openEdgeDataSource = openEdgeDataSource;

        // Registrar por defecto el DataSource del catálogo (MySQL)
        registerDataSource("mysql-gateway", catalogDataSource);
        registerDataSource("MYSQL_GATEWAY", catalogDataSource);

        // Registrar el DataSource de OpenEdge si está disponible
        if (openEdgeDataSource != null) {
            registerDataSource("openedge-mfg", openEdgeDataSource);
            registerDataSource("OPENEDGE_MFG", openEdgeDataSource);
        }
    }

    public synchronized void registerDataSource(String datasourceId, DataSource ds) {
        dataSourceMap.put(datasourceId.toLowerCase(), ds);
        dataSourceMap.put(datasourceId, ds);
        NamedParameterJdbcTemplate np = new NamedParameterJdbcTemplate(ds);
        JdbcTemplate jt = new JdbcTemplate(ds);
        namedJdbcTemplateMap.put(datasourceId.toLowerCase(), np);
        namedJdbcTemplateMap.put(datasourceId, np);
        jdbcTemplateMap.put(datasourceId.toLowerCase(), jt);
        jdbcTemplateMap.put(datasourceId, jt);
        log.info("DYNAMIC_DS: DataSource '{}' registrado para enrutamiento dinámico.", datasourceId);
    }

    public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(String datasourceId) {
        if (datasourceId == null || datasourceId.isBlank()) {
            return namedJdbcTemplateMap.getOrDefault("openedge-mfg", 
                    namedJdbcTemplateMap.get("mysql-gateway"));
        }

        NamedParameterJdbcTemplate np = namedJdbcTemplateMap.get(datasourceId.toLowerCase());
        if (np != null) {
            return np;
        }

        // Si no está registrado en memoria, intentar resolver desde gateway.datasources
        DataSource ds = resolveFromProperties(datasourceId);
        if (ds != null) {
            registerDataSource(datasourceId, ds);
            return namedJdbcTemplateMap.get(datasourceId.toLowerCase());
        }

        // Fallback al catálogo o por defecto
        log.warn("DYNAMIC_DS: No se encontró datasourceId '{}', usando default.", datasourceId);
        return namedJdbcTemplateMap.getOrDefault("openedge-mfg", 
                namedJdbcTemplateMap.get("mysql-gateway"));
    }

    public DataSource getDataSource(String datasourceId) {
        if (datasourceId == null || datasourceId.isBlank()) {
            return dataSourceMap.getOrDefault("openedge-mfg", catalogDataSource);
        }
        DataSource ds = dataSourceMap.get(datasourceId.toLowerCase());
        if (ds != null) {
            return ds;
        }
        ds = resolveFromProperties(datasourceId);
        if (ds != null) {
            registerDataSource(datasourceId, ds);
            return ds;
        }
        return dataSourceMap.getOrDefault("openedge-mfg", catalogDataSource);
    }

    @SuppressWarnings("unchecked")
    private DataSource resolveFromProperties(String datasourceId) {
        Map<String, Object> dsProps = (Map<String, Object>) gatewayProperties.getDatasources().get(datasourceId);
        if (dsProps == null) {
            return null;
        }

        String typeStr = (String) dsProps.get("type");
        if (typeStr == null) {
            return null;
        }

        com.gateway.dialect.EngineType engineType;
        try {
            engineType = com.gateway.dialect.EngineType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("DYNAMIC_DS: Tipo de motor '{}' no reconocido para datasource '{}'", typeStr, datasourceId);
            return null;
        }

        String url = (String) dsProps.get("jdbc-url");
        String user = (String) dsProps.get("username");
        String pass = (String) dsProps.get("password");
        String defaultDriver;
        String defaultValidationQuery;

        switch (engineType) {
            case MYSQL -> {
                defaultDriver = "com.mysql.cj.jdbc.Driver";
                defaultValidationQuery = "SELECT 1";
            }
            case POSTGRESQL -> {
                defaultDriver = "org.postgresql.Driver";
                defaultValidationQuery = "SELECT 1";
            }
            case SQLSERVER -> {
                defaultDriver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                defaultValidationQuery = "SELECT 1";
            }
            case ORACLE -> {
                defaultDriver = "oracle.jdbc.OracleDriver";
                defaultValidationQuery = "SELECT 1 FROM DUAL";
            }
            case OPENEDGE -> {
                defaultDriver = "com.ddtek.jdbc.openedge.OpenEdgeDriver";
                defaultValidationQuery = "SELECT 1 FROM sysprogress.SYSTABLES WHERE TBL = 'SYSTABLES'";
                if (url == null) {
                    String host = (String) dsProps.get("host");
                    Object port = dsProps.getOrDefault("port", 18111);
                    String db = (String) dsProps.get("database");
                    url = String.format("jdbc:datadirect:openedge://%s:%s;DatabaseName=%s", host, port, db);
                }
            }
            default -> {
                return null;
            }
        }

        String driver = (String) dsProps.getOrDefault("driver-class-name", defaultDriver);
        if (url == null) {
            log.warn("DYNAMIC_DS: Falta jdbc-url para el datasource '{}'", datasourceId);
            return null;
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setPoolName("DynamicPool-" + datasourceId);
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(5);
        config.setConnectionTestQuery(defaultValidationQuery);

        log.info("DYNAMIC_DS: Inicializando HikariDataSource para '{}' tipo [{}] con driver [{}]", 
                datasourceId, engineType, driver);
        return new HikariDataSource(config);
    }

    @PreDestroy
    public void cleanup() {
        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            if (entry.getValue() instanceof HikariDataSource hds && 
                entry.getValue() != catalogDataSource && 
                entry.getValue() != openEdgeDataSource) {
                try {
                    hds.close();
                } catch (Exception ignored) {}
            }
        }
    }
}