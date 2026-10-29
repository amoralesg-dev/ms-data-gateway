-- =====================================================================
-- DDL Y CONFIGURACIÓN INICIAL: BASE DE DATOS Y USUARIO DE CONTROL GATEWAY
-- Motor: MySQL 8.0 (rasinniDB)
-- Base de datos: `gateway-data`
-- Usuario: `gateway-data`
-- =====================================================================

-- 1. Creación de la base de datos
CREATE DATABASE IF NOT EXISTS `gateway-data`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 2. Creación del usuario con permisos para localhost y host remoto (%)
-- REEMPLAZAR '<CHANGE_ME_DB_PASSWORD>' por la contraseña segura del ambiente antes de ejecutar
CREATE USER IF NOT EXISTS 'gateway-data'@'%' IDENTIFIED BY '<CHANGE_ME_DB_PASSWORD>';
CREATE USER IF NOT EXISTS 'gateway-data'@'localhost' IDENTIFIED BY '<CHANGE_ME_DB_PASSWORD>';

-- 3. Otorgar permisos sobre la base gateway-data
GRANT ALL PRIVILEGES ON `gateway-data`.* TO 'gateway-data'@'%';
GRANT ALL PRIVILEGES ON `gateway-data`.* TO 'gateway-data'@'localhost';
FLUSH PRIVILEGES;

USE `gateway-data`;

-- 4. Creación de la tabla: gateway_datasource
CREATE TABLE IF NOT EXISTS `gateway_datasource` (
    `datasource_id` VARCHAR(50) NOT NULL,
    `datasource_code` VARCHAR(50) NOT NULL,
    `datasource_name` VARCHAR(100) NOT NULL,
    `engine_type` VARCHAR(30) NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `default_flag` TINYINT(1) NOT NULL DEFAULT 0,
    `description` VARCHAR(255) NULL,
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`datasource_id`),
    UNIQUE KEY `uk_ds_code` (`datasource_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Creación de la tabla: gateway_query
CREATE TABLE IF NOT EXISTS `gateway_query` (
    `query_code` VARCHAR(100) NOT NULL,
    `query_name` VARCHAR(150) NOT NULL,
    `datasource_id` VARCHAR(50) NOT NULL,
    `query_type` VARCHAR(20) NOT NULL, -- SELECT, INSERT, UPDATE, DELETE
    `sql_text` TEXT NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `timeout_seconds` INT NOT NULL DEFAULT 30,
    `max_rows` INT NOT NULL DEFAULT 1000,
    `description` VARCHAR(255) NULL,
    `created_by` VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50) NULL,
    `updated_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`query_code`),
    CONSTRAINT `fk_gq_datasource` FOREIGN KEY (`datasource_id`) REFERENCES `gateway_datasource` (`datasource_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Creación de la tabla: gateway_query_parameter
CREATE TABLE IF NOT EXISTS `gateway_query_parameter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `query_code` VARCHAR(100) NOT NULL,
    `parameter_name` VARCHAR(50) NOT NULL,
    `parameter_type` VARCHAR(30) NOT NULL DEFAULT 'STRING', -- STRING, INTEGER, DECIMAL, DATE, TIMESTAMP, BOOLEAN
    `required_flag` TINYINT(1) NOT NULL DEFAULT 0,
    `default_value` VARCHAR(255) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_query_param` (`query_code`, `parameter_name`),
    CONSTRAINT `fk_gqp_query` FOREIGN KEY (`query_code`) REFERENCES `gateway_query` (`query_code`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Creación de la tabla: gateway_query_execution
CREATE TABLE IF NOT EXISTS `gateway_query_execution` (
    `execution_id` BIGINT NOT NULL AUTO_INCREMENT,
    `query_code` VARCHAR(100) NOT NULL,
    `datasource_id` VARCHAR(50) NULL,
    `executed_by` VARCHAR(50) NOT NULL,
    `execution_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `execution_time_ms` BIGINT NOT NULL,
    `rows_returned` INT NOT NULL DEFAULT 0,
    `success` TINYINT(1) NOT NULL,
    `error_message` TEXT NULL,
    `source_ip` VARCHAR(45) NULL,
    PRIMARY KEY (`execution_id`),
    KEY `idx_gqe_query_code` (`query_code`),
    KEY `idx_gqe_date` (`execution_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 8. TABLAS DE CATÁLOGO Y AUDITORÍA DE SERVICIOS QAD (QXtend SOAP)
-- =====================================================================

-- 8.1 Catálogo de Servicios de Negocio QAD
CREATE TABLE IF NOT EXISTS `gateway_service` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `service_code` VARCHAR(50) NOT NULL,
    `service_name` VARCHAR(100) NOT NULL,
    `service_type` VARCHAR(30) NOT NULL DEFAULT 'SOAP_QXtend', -- SOAP_QXtend, REST, etc.
    `mapper_class` VARCHAR(255) NOT NULL,
    `operation_name` VARCHAR(100) NOT NULL,
    `endpoint_url` VARCHAR(500) NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `timeout_seconds` INT NOT NULL DEFAULT 60,
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gs_code` (`service_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8.2 Auditoría de Ejecución de Servicios QAD
CREATE TABLE IF NOT EXISTS `gateway_service_execution` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `service_code` VARCHAR(50) NOT NULL,
    `request_payload` MEDIUMTEXT NULL,
    `response_payload` MEDIUMTEXT NULL,
    `status` VARCHAR(20) NOT NULL, -- SUCCESS, ERROR, FAULT
    `error_code` VARCHAR(100) NULL,
    `error_message` TEXT NULL,
    `execution_time_ms` BIGINT NOT NULL DEFAULT 0,
    `executed_by` VARCHAR(50) NOT NULL DEFAULT 'api-client',
    `source_ip` VARCHAR(45) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_gse_service_code` (`service_code`),
    KEY `idx_gse_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8.3 Inserción de Servicios Iniciales (BCreditor)
INSERT INTO `gateway_service` (`service_code`, `service_name`, `service_type`, `mapper_class`, `operation_name`, `endpoint_url`, `enabled`, `timeout_seconds`)
VALUES 
('BCREDITOR', 'QAD Creditor / Supplier Maintenance Service', 'SOAP_QXtend', 'com.gateway.qad.mapper.impl.BCreditorMapper', 'bcreditor', 'http://qvarrazldb02.odqad.com:8180/qxiqonddb/services/QdocWebService', 1, 60)
ON DUPLICATE KEY UPDATE 
    `service_name` = VALUES(`service_name`),
    `service_type` = VALUES(`service_type`),
    `mapper_class` = VALUES(`mapper_class`),
    `operation_name` = VALUES(`operation_name`),
    `endpoint_url` = VALUES(`endpoint_url`),
    `enabled` = VALUES(`enabled`);

-- =====================================================================
-- 9. Inserción de DataSources Iniciales
-- =====================================================================
INSERT INTO `gateway_datasource` (`datasource_id`, `datasource_code`, `datasource_name`, `engine_type`, `enabled`, `default_flag`, `description`)
VALUES 
('openedge-mfg', 'OPENEDGE_MFG', 'OpenEdge MFG Manufacturing Database', 'OPENEDGE', 1, 1, 'Base de datos principal Progress OpenEdge qvarrazldb01'),
('mysql-gateway', 'MYSQL_GATEWAY', 'MySQL Gateway Control & Catalog Database', 'MYSQL', 1, 0, 'Base de datos MySQL de catálogo y auditoría rasinniDB')
ON DUPLICATE KEY UPDATE 
    `datasource_name` = VALUES(`datasource_name`),
    `engine_type` = VALUES(`engine_type`),
    `enabled` = VALUES(`enabled`);

-- =====================================================================
-- 10. EJEMPLOS COMENTADOS DE REGISTRO PARA OTROS MOTORES
-- Descomentar y ejecutar según se requiera integrar nuevas fuentes:
-- =====================================================================
-- INSERT INTO `gateway_datasource` (`datasource_id`, `datasource_code`, `datasource_name`, `engine_type`, `enabled`, `default_flag`, `description`)
-- VALUES 
-- ('postgres-crm', 'POSTGRES_CRM', 'PostgreSQL CRM Clientes', 'POSTGRESQL', 1, 0, 'Base de datos de fidelización y clientes CRM en PostgreSQL'),
-- ('sqlserver-erp', 'SQLSERVER_ERP', 'Microsoft SQL Server ERP Corporativo', 'SQLSERVER', 1, 0, 'Base de datos transaccional ERP en SQL Server'),
-- ('oracle-wms', 'ORACLE_WMS', 'Oracle Warehouse Management System (WMS)', 'ORACLE', 1, 0, 'Sistema de inventarios y almacenes en Oracle Database')
-- ON DUPLICATE KEY UPDATE `datasource_name` = VALUES(`datasource_name`), `engine_type` = VALUES(`engine_type`);


