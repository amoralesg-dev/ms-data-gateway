package com.gateway.qad.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "qad.qxtend")
public class QadQxtendProperties {
    /**
     * Endpoint por defecto del servicio SOAP QXtend (SoapBinding / QdocWebService)
     */
    private String endpointUrl = "http://qvarrazldb02.odqad.com:8180/qxiqonddb/services/QdocWebService";

    /**
     * Dominio QAD por defecto (ej. MXMGT01)
     */
    private String defaultDomain = "MXMGT01";

    /**
     * Versión QAD por defecto (ej. ERP3_1)
     */
    private String defaultVersion = "ERP3_1";

    /**
     * Entidad QAD por defecto (ej. 0109, 0111)
     */
    private String defaultEntity = "0109";

    /**
     * Company code por defecto para el contexto de negocio
     */
    private String defaultCompanyCode = "0111";

    /**
     * Acción por defecto en el session context (SAVE)
     */
    private String defaultAction = "SAVE";

    /**
     * Timeout de conexión en segundos
     */
    private int connectTimeoutSeconds = 15;


    /**
     * Timeout de lectura en segundos
     */
    private int readTimeoutSeconds = 60;
}
