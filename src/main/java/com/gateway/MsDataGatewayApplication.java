package com.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Microservicio Data Gateway para Progress OpenEdge.
 *
 * <p>Se excluye {@link DataSourceAutoConfiguration} porque el DataSource
 * se configura manualmente en {@link com.gateway.config.OpenEdgeDataSourceConfig}
 * para tener control total sobre los parámetros SSL de la conexión JDBC.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ConfigurationPropertiesScan
public class MsDataGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsDataGatewayApplication.class, args);
    }
}
