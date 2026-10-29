package com.gateway.qad;

import com.gateway.qad.dto.QadServiceResponse;
import com.gateway.qad.parser.QadSoapResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QadSoapResponseParserTest {

    private QadSoapResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new QadSoapResponseParser();
    }

    @Test
    @DisplayName("Debe parsear exitosamente respuesta de éxito de QAD (Escenario 1)")
    void testParseSuccessResponse() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:qcom=\"urn:schemas-qad-com:xml-services:common\">\n" +
                "  <soapenv:Body>\n" +
                "    <bcreditorResponse xmlns=\"urn:schemas-qad-com:xml-services\">\n" +
                "      <result>success</result>\n" +
                "      <qcom:dsSessionContext>\n" +
                "        <qcom:ttContext>\n" +
                "          <qcom:propertyQualifier>QAD</qcom:propertyQualifier>\n" +
                "          <qcom:propertyName>apiName</qcom:propertyName>\n" +
                "          <qcom:propertyValue>bcreditor</qcom:propertyValue>\n" +
                "        </qcom:ttContext>\n" +
                "        <qcom:ttContext>\n" +
                "          <qcom:propertyQualifier>QAD</qcom:propertyQualifier>\n" +
                "          <qcom:propertyName>programName</qcom:propertyName>\n" +
                "          <qcom:propertyValue>BCreditor</qcom:propertyValue>\n" +
                "        </qcom:ttContext>\n" +
                "        <qcom:ttContext>\n" +
                "          <qcom:propertyQualifier>QAD</qcom:propertyQualifier>\n" +
                "          <qcom:propertyName>domain</qcom:propertyName>\n" +
                "          <qcom:propertyValue>MXMGT01</qcom:propertyValue>\n" +
                "        </qcom:ttContext>\n" +
                "        <qcom:ttContext>\n" +
                "          <qcom:propertyQualifier>QAD</qcom:propertyQualifier>\n" +
                "          <qcom:propertyName>BLFSessionId</qcom:propertyName>\n" +
                "          <qcom:propertyValue>session-xyz-123</qcom:propertyValue>\n" +
                "        </qcom:ttContext>\n" +
                "      </qcom:dsSessionContext>\n" +
                "    </bcreditorResponse>\n" +
                "  </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        QadServiceResponse response = parser.parse(xml, 250);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("exitosamente");
        assertThat(response.getContext()).containsEntry("apiName", "bcreditor");
        assertThat(response.getContext()).containsEntry("domain", "MXMGT01");
        assertThat(response.getContext()).containsEntry("BLFSessionId", "session-xyz-123");
        assertThat(response.getExecutionTimeMs()).isEqualTo(250);
    }


    @Test
    @DisplayName("Debe parsear error funcional de negocio de QAD con dsExceptions (Escenario 2)")
    void testParseFunctionalErrorResponse() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:qcom=\"urn:schemas-qad-com:xml-services:common\">\n" +
                "  <soapenv:Body>\n" +
                "    <bcreditorResponse xmlns=\"urn:schemas-qad-com:xml-services\">\n" +
                "      <result>error</result>\n" +
                "      <qcom:dsExceptions>\n" +
                "        <qcom:temp_err_msg>\n" +
                "          <qcom:tt_msg_nbr>GEN-O-2</qcom:tt_msg_nbr>\n" +
                "          <qcom:tt_msg_desc>This value already exists.</qcom:tt_msg_desc>\n" +
                "          <qcom:tt_msg_data>Supplier (tCreditor.CreditorCode) 60000011</qcom:tt_msg_data>\n" +
                "          <qcom:tt_msg_field>CreditorCode</qcom:tt_msg_field>\n" +
                "          <qcom:tt_msg_sev>ERROR</qcom:tt_msg_sev>\n" +
                "        </qcom:temp_err_msg>\n" +
                "      </qcom:dsExceptions>\n" +
                "    </bcreditorResponse>\n" +
                "  </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        QadServiceResponse response = parser.parse(xml, 180);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("GEN-O-2");
        assertThat(response.getMessage()).contains("This value already exists");
        assertThat(response.getMessage()).contains("60000011");
        assertThat(response.getExceptions()).hasSize(1);
        assertThat(response.getExceptions().get(0).getMessageField()).isEqualTo("CreditorCode");
    }

    @Test
    @DisplayName("Debe detectar SOAP Fault técnico (Escenario 3)")
    void testParseSoapFault() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "  <soapenv:Body>\n" +
                "    <soapenv:Fault>\n" +
                "      <faultcode>soapenv:Server</faultcode>\n" +
                "      <faultstring>QDoc is not supported. Unable to find session context</faultstring>\n" +
                "    </soapenv:Fault>\n" +
                "  </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        QadServiceResponse response = parser.parse(xml, 95);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("SOAP_FAULT");
        assertThat(response.getMessage()).contains("QDoc is not supported");
        assertThat(response.getMessage()).contains("Unable to find session context");
    }
}

