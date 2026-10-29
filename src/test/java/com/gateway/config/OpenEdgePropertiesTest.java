package com.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenEdgePropertiesTest {

    @Test
    @DisplayName("Debe construir la URL JDBC con SSL y ciphers validados de DBeaver sin exponer credenciales")
    void testBuildJdbcUrl() {
        OpenEdgeProperties props = new OpenEdgeProperties();
        props.setHost("qvarrazldb01");
        props.setPort(18111);
        props.setDatabase("mfgdb");
        props.setUsername("myuser");
        props.setPassword("secretpass");
        props.setEncryptionMethod("ssl");
        props.setCryptoProtocolVersion("TLSv1.2");
        props.setEnableCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256");
        props.setValidateServerCertificate(false);

        String url = props.buildJdbcUrl();

        // 1. Host, port, database
        assertThat(url).contains("jdbc:datadirect:openedge://qvarrazldb01:18111");
        assertThat(url).contains("databaseName=mfgdb");

        // 2. Parámetros SSL exactos validados
        assertThat(url).contains("encryptionMethod=ssl");
        assertThat(url).contains("CryptoProtocolVersion=TLSv1.2");
        assertThat(url).contains("EnableCipherSuites=TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA256");
        assertThat(url).contains("validateServerCertificate=false");

        // 3. NO debe exponer usuario ni password en la cadena de URL
        assertThat(url).doesNotContain("myuser");
        assertThat(url).doesNotContain("secretpass");
        assertThat(url).doesNotContain("User=");
        assertThat(url).doesNotContain("Password=");
    }
}
