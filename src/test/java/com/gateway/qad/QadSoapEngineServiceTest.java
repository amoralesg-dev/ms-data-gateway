package com.gateway.qad;

import com.gateway.catalog.GatewayCatalogRepository;
import com.gateway.catalog.GatewayServiceEntity;
import com.gateway.qad.client.QadSoapClient;
import com.gateway.qad.client.QadSoapClient.SoapCallResult;
import com.gateway.qad.config.QadQxtendProperties;
import com.gateway.qad.dto.BCreditorRequest;
import com.gateway.qad.dto.QadServiceResponse;
import com.gateway.qad.envelope.QadSoapEnvelopeBuilder;
import com.gateway.qad.mapper.impl.BCreditorMapper;
import com.gateway.qad.parser.QadSoapResponseParser;
import com.gateway.qad.registry.QadServiceRegistry;
import com.gateway.qad.service.QadSoapEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QadSoapEngineServiceTest {

    @Mock
    private QadSoapClient soapClient;

    @Mock
    private GatewayCatalogRepository catalogRepository;

    private QadSoapEngineService engineService;

    @BeforeEach
    void setUp() {
        QadQxtendProperties properties = new QadQxtendProperties();
        BCreditorMapper bcreditorMapper = new BCreditorMapper(properties);
        QadSoapEnvelopeBuilder envelopeBuilder = new QadSoapEnvelopeBuilder();
        QadSoapResponseParser responseParser = new QadSoapResponseParser();
        QadServiceRegistry serviceRegistry = new QadServiceRegistry(Optional.of(catalogRepository), List.of(bcreditorMapper));
        serviceRegistry.init();

        ObjectMapper objectMapper = new ObjectMapper();

        engineService = new QadSoapEngineService(
                soapClient,
                envelopeBuilder,
                responseParser,
                serviceRegistry,
                Optional.of(catalogRepository),
                objectMapper
        );
    }

    @Test
    @DisplayName("Debe orquestar exitosamente llamada a QAD retornando QadServiceResponse exitosa")
    void testExecuteSuccess() throws Exception {
        when(catalogRepository.findServiceByCode("BCREDITOR")).thenReturn(Optional.of(
                GatewayServiceEntity.builder()
                        .serviceCode("BCREDITOR")
                        .endpointUrl("http://localhost:8180/qxi/services/SoapBinding")
                        .enabled(true)
                        .timeoutSeconds(30)
                        .build()
        ));

        String mockSuccessXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "  <soapenv:Body>\n" +
                "    <bcreditorResponse xmlns=\"urn:schemas-qad-com:xml-services\">\n" +
                "      <result>success</result>\n" +
                "    </bcreditorResponse>\n" +
                "  </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        when(soapClient.sendSoapRequest(anyString(), anyString(), anyInt()))
                .thenReturn(new SoapCallResult(200, mockSuccessXml, 150));

        BCreditorRequest req = BCreditorRequest.builder()
                .creditorCode("60000011")
                .businessRelationName1("TEST SUPPLIER")
                .build();

        QadServiceResponse response = engineService.createOrUpdateCreditor(req, "admin-user", "127.0.0.1");

        assertThat(response.isSuccess()).isTrue();
        verify(soapClient, times(1)).sendSoapRequest(eq("http://localhost:8180/qxi/services/SoapBinding"), anyString(), eq(30));
        verify(catalogRepository, times(1)).recordServiceExecution(any());
    }

    @Test
    @DisplayName("Debe orquestar y mapear error de validacion funcional (GEN-O-2)")
    void testExecuteFunctionalError() throws Exception {
        when(catalogRepository.findServiceByCode("BCREDITOR")).thenReturn(Optional.empty());

        String mockErrorXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:qcom=\"urn:schemas-qad-com:xml-services:common\">\n" +
                "  <soapenv:Body>\n" +
                "    <bcreditorResponse xmlns=\"urn:schemas-qad-com:xml-services\">\n" +
                "      <result>error</result>\n" +
                "      <qcom:dsExceptions>\n" +
                "        <qcom:temp_err_msg>\n" +
                "          <qcom:tt_msg_nbr>GEN-O-2</qcom:tt_msg_nbr>\n" +
                "          <qcom:tt_msg_desc>This value already exists.</qcom:tt_msg_desc>\n" +
                "          <qcom:tt_msg_data>Supplier 60000011</qcom:tt_msg_data>\n" +
                "        </qcom:temp_err_msg>\n" +
                "      </qcom:dsExceptions>\n" +
                "    </bcreditorResponse>\n" +
                "  </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        when(soapClient.sendSoapRequest(any(), anyString(), anyInt()))
                .thenReturn(new SoapCallResult(200, mockErrorXml, 110));

        BCreditorRequest req = BCreditorRequest.builder()
                .creditorCode("60000011")
                .businessRelationName1("TEST SUPPLIER")
                .build();

        QadServiceResponse response = engineService.createOrUpdateCreditor(req, "admin-user", "127.0.0.1");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("GEN-O-2");
        assertThat(response.getMessage()).contains("This value already exists");
        verify(catalogRepository, times(1)).recordServiceExecution(argThat(exec -> "ERROR".equals(exec.getStatus())));
    }
}

