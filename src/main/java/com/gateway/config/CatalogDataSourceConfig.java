package com.gateway.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuración del DataSource de Control y Catálogo (MySQL).
 * Este DataSource es @Primary para garantizar que la infraestructura interna
 * del gateway (tablas gateway_datasource, gateway_query, etc.) siempre opere
 * contra la base de datos de gestión, independientemente del estado de las fuentes de negocio externas.
 */
@Configuration
public class CatalogDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource catalogDataSource(DataSourceProperties properties) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName(properties.getDriverClassName());
        ds.setJdbcUrl(properties.getUrl());
        ds.setUsername(properties.getUsername());
        ds.setPassword(properties.getPassword());
        ds.setPoolName("GatewayCatalogPool");
        return ds;
    }

    @Bean(name = "catalogJdbcTemplate")
    @Primary
    public JdbcTemplate catalogJdbcTemplate(@Qualifier("catalogDataSource") DataSource catalogDataSource) {
        return new JdbcTemplate(catalogDataSource);
    }

    @Bean(name = "catalogNamedParameterJdbcTemplate")
    @Primary
    public NamedParameterJdbcTemplate catalogNamedParameterJdbcTemplate(@Qualifier("catalogDataSource") DataSource catalogDataSource) {
        return new NamedParameterJdbcTemplate(catalogDataSource);
    }
}