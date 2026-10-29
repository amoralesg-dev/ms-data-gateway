package com.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.hikari.connection-test-query=SELECT 1",
    "gateway.security.api-keys[0]=test-key",
    "gateway.registered-queries.test-query.sql=SELECT 1",
    "gateway.registered-queries.test-query.type=SELECT",
    "gateway.registered-queries.test-query.description=Test query"
})
class MsDataGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Verifica que el contexto de Spring se carga correctamente
    }
}
