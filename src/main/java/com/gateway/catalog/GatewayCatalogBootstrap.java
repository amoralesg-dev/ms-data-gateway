package com.gateway.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bootstrap de inicialización del catálogo persistente.
 * 
 * Si la tabla gateway_query está vacía:
 * - Lee queries-examples.yml
 * - Asocia las consultas al datasource por defecto (openedge-mfg)
 * - Extrae los parámetros nombrados (:param) y los registra en gateway_query_parameter
 * - Inserta los registros en gateway_query
 * 
 * Si la tabla ya contiene registros:
 * - Omite la carga inicial y utiliza los datos existentes en la base de control.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayCatalogBootstrap {

    private final GatewayCatalogRepository catalogRepository;
    private final ResourceLoader resourceLoader;
    private static final Pattern PARAM_PATTERN = Pattern.compile(":([a-zA-Z0-9_]+)");

    @PostConstruct
    public void init() {
        try {
            int existingCount = catalogRepository.countQueries();
            if (existingCount > 0) {
                log.info("CATALOG_BOOTSTRAP: Se detectaron {} consultas en gateway_query. Utilizando catálogo persistente existente.", existingCount);
                return;
            }

            log.info("CATALOG_BOOTSTRAP: La tabla gateway_query está vacía. Iniciando carga automática desde queries-examples.yml...");
            Resource resource = resourceLoader.getResource("classpath:queries-examples.yml");
            if (!resource.exists()) {
                log.warn("CATALOG_BOOTSTRAP: No se encontró queries-examples.yml en el classpath. Omitiendo bootstrap.");
                return;
            }

            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = yamlMapper.readTree(in);
                JsonNode queriesNode = root.path("gateway").path("registered-queries");

                if (queriesNode.isMissingNode() || !queriesNode.isObject()) {
                    log.warn("CATALOG_BOOTSTRAP: No se encontró el bloque 'gateway.registered-queries' en queries-examples.yml.");
                    return;
                }

                Iterator<Map.Entry<String, JsonNode>> fields = queriesNode.fields();
                int insertedCount = 0;

                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String queryCode = entry.getKey();
                    JsonNode node = entry.getValue();

                    String sql = node.path("sql").asText("");
                    String description = node.path("description").asText("");
                    String type = node.path("type").asText("SELECT");

                    GatewayQueryEntity queryEntity = GatewayQueryEntity.builder()
                            .queryCode(queryCode)
                            .queryName(queryCode.replace('-', ' ').toUpperCase())
                            .datasourceId("openedge-mfg")
                            .queryType(type)
                            .sqlText(sql)
                            .enabled(true)
                            .timeoutSeconds(30)
                            .maxRows(1000)
                            .description(description)
                            .createdBy("BOOTSTRAP")
                            .build();

                    catalogRepository.insertQuery(queryEntity);
                    insertedCount++;

                    // Detectar y guardar parámetros nombrados (:paramName)
                    Matcher matcher = PARAM_PATTERN.matcher(sql);
                    while (matcher.find()) {
                        String paramName = matcher.group(1);
                        GatewayQueryParameterEntity param = GatewayQueryParameterEntity.builder()
                                .queryCode(queryCode)
                                .parameterName(paramName)
                                .parameterType("STRING")
                                .requiredFlag(true)
                                .build();
                        try {
                            catalogRepository.insertParameter(param);
                        } catch (Exception ex) {
                            log.debug("Parámetro ya registrado: {} para {}", paramName, queryCode);
                        }
                    }
                }

                log.info("CATALOG_BOOTSTRAP_SUCCESS: Se insertaron {} consultas iniciales y sus parámetros en la base de control MySQL.", insertedCount);
            }
        } catch (Exception e) {
            log.warn("CATALOG_BOOTSTRAP_WARNING: No fue posible completar el bootstrap en MySQL: {}. La aplicación continuará operativa.", e.getMessage());
        }
    }
}
