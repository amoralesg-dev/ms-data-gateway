package com.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * DataSource de respaldo para entornos donde no está configurado OpenEdge
 * (como pruebas unitarias/de integración locales con base en memoria).
 */
@Configuration
public class FallbackDataSourceConfig {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource fallbackDataSource(org.springframework.boot.autoconfigure.jdbc.DataSourceProperties properties) {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setDriverClassName(properties.getDriverClassName());
        ds.setJdbcUrl(properties.getUrl());
        ds.setUsername(properties.getUsername());
        ds.setPassword(properties.getPassword());
        ds.setPoolName("GatewayCatalogPool");
        return ds;
    }

    @Bean
    @ConditionalOnMissingBean(JdbcTemplate.class)
    public JdbcTemplate fallbackJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(NamedParameterJdbcTemplate.class)
    public NamedParameterJdbcTemplate fallbackNamedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
