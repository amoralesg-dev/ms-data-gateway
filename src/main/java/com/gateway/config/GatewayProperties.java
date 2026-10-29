package com.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Security security = new Security();
    private Queries queries = new Queries();
    private Operations operations = new Operations();
    private Map<String, RegisteredQuery> registeredQueries = new java.util.LinkedHashMap<>();
    private Map<String, Object> datasources = new java.util.LinkedHashMap<>();

    private AdminQuery adminQuery = new AdminQuery();

    @Data
    public static class Security {
        private String apiKeyHeader = "X-API-Key";
        private List<String> apiKeys = new java.util.ArrayList<>();
        private String adminKeyHeader = "X-Admin-Key";
        private List<String> adminKeys = new java.util.ArrayList<>();
    }

    @Data
    public static class Queries {
        private int maxRows = 1000;
        private int defaultPageSize = 50;
    }

    @Data
    public static class AdminQuery {
        private int maxPageSize = 1000;
        private int defaultPageSize = 100;
    }

    @Data
    public static class Operations {
        private boolean deleteEnabled = true;
        private boolean auditEnabled = true;
    }

    @Data
    public static class RegisteredQuery {
        private String sql;
        private String description;
        private String type; // SELECT, INSERT, UPDATE, DELETE
    }
}
