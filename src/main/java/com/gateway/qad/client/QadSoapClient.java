package com.gateway.qad.client;

import com.gateway.qad.config.QadQxtendProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cliente HTTP SOAP para invocar servicios QXtend / QdocWebService.
 * Utiliza java.net.http.HttpClient nativo de Java 21 para máximo rendimiento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QadSoapClient {

    private final QadQxtendProperties properties;

    /**
     * Realiza la llamada HTTP POST SOAP a QXtend.
     *
     * @param targetUrl URL del servicio SOAP (si es nulo usa la configurada en properties)
     * @param soapEnvelope Contenido XML completo del sobre SOAP
     * @param timeoutSeconds Timeout opcional en segundos
     * @return SoapCallResult con el código de respuesta HTTP, cuerpo XML y tiempo transcurrido
     */
    public SoapCallResult sendSoapRequest(String targetUrl, String soapEnvelope, int timeoutSeconds) throws IOException, InterruptedException {
        String endpoint = (targetUrl != null && !targetUrl.isBlank()) ? targetUrl : properties.getEndpointUrl();
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : properties.getReadTimeoutSeconds();

        log.debug("Enviando request SOAP a {}. Longitud XML: {} bytes", endpoint, soapEnvelope.length());

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(soapEnvelope))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long executionTimeMs = System.currentTimeMillis() - start;

        log.debug("Respuesta SOAP recibida de {} con HTTP {}. Tiempo: {} ms", endpoint, response.statusCode(), executionTimeMs);

        return new SoapCallResult(response.statusCode(), response.body(), executionTimeMs);
    }

    public record SoapCallResult(int httpStatusCode, String responseBody, long executionTimeMs) {}
}

