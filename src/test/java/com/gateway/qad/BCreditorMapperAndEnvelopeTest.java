package com.gateway.qad;

import com.gateway.qad.config.QadQxtendProperties;
import com.gateway.qad.dto.BCreditorRequest;
import com.gateway.qad.envelope.QadSessionContext;
import com.gateway.qad.envelope.QadSoapEnvelopeBuilder;
import com.gateway.qad.mapper.impl.BCreditorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCreditorMapperAndEnvelopeTest {

    private BCreditorMapper mapper;
    private QadSoapEnvelopeBuilder envelopeBuilder;

    @BeforeEach
    void setUp() {
        QadQxtendProperties properties = new QadQxtendProperties();
        mapper = new BCreditorMapper(properties);
        envelopeBuilder = new QadSoapEnvelopeBuilder();
    }

    @Test
    @DisplayName("Debe mapear BCreditorRequest a XML con estructura QAD adecuada")
    void testToXmlPayload() {
        BCreditorRequest request = BCreditorRequest.builder()
                .creditorCode("60000011")
                .businessRelationName1("HUGO CESAR MUNTANE RODRIGUEZ")
                .currencyCode("MXN")
                .taxZone("MX")
                .taxClass("HON")
                .paymentConditionCode("30")
                .build();

        String xml = mapper.toXmlPayload(request);

        assertThat(xml).contains("<BCreditor");
        assertThat(xml).contains("<tcCompanyCode>0111</tcCompanyCode>");
        assertThat(xml).contains("<tcAction>SAVE</tcAction>");
        assertThat(xml).contains("<CreditorCode>60000011</CreditorCode>");
        assertThat(xml).contains("<tcBusinessRelationName1>HUGO CESAR MUNTANE RODRIGUEZ</tcBusinessRelationName1>");
        assertThat(xml).contains("<tcCurrencyCode>MXN</tcCurrencyCode>");
        assertThat(xml).contains("<TxzTaxZone>MX</TxzTaxZone>");
        assertThat(xml).contains("<TxclTaxCls>HON</TxclTaxCls>");
        assertThat(xml).contains("<tcNormalPaymentConditionCode>30</tcNormalPaymentConditionCode>");
    }

    @Test
    @DisplayName("Debe generar Envelope SOAP con WS-Addressing y qcom:dsSessionContext obligatorio")
    void testBuildEnvelope() {
        BCreditorRequest request = BCreditorRequest.builder()
                .creditorCode("60000011")
                .businessRelationName1("HUGO CESAR MUNTANE RODRIGUEZ")
                .build();

        String payload = mapper.toXmlPayload(request);
        QadSessionContext sessionCtx = mapper.buildSessionContext(request);

        String envelope = envelopeBuilder.buildEnvelope(mapper.getOperationName(), sessionCtx, payload);

        // Validar Envelope y Namespaces
        assertThat(envelope).contains("xmlns=\"urn:schemas-qad-com:xml-services\"");
        assertThat(envelope).contains("xmlns:qcom=\"urn:schemas-qad-com:xml-services:common\"");
        assertThat(envelope).contains("xmlns:wsa=\"http://www.w3.org/2005/08/addressing\"");
        assertThat(envelope).contains("<soapenv:Envelope");

        // Validar WS-Addressing
        assertThat(envelope).contains("<wsa:Action/>");
        assertThat(envelope).contains("<wsa:To>urn:services-qad-com:QADERP</wsa:To>");
        assertThat(envelope).contains("<wsa:MessageID>urn:services-qad-com::QADERP:");
        assertThat(envelope).contains("<qcom:suppressResponseDetail>true</qcom:suppressResponseDetail>");

        // Validar qcom:dsSessionContext obligatorio
        assertThat(envelope).contains("<qcom:dsSessionContext>");
        assertThat(envelope).contains("<qcom:propertyName>domain</qcom:propertyName>");
        assertThat(envelope).contains("<qcom:propertyValue>MXMGT01</qcom:propertyValue>");
        assertThat(envelope).contains("<qcom:propertyName>version</qcom:propertyName>");
        assertThat(envelope).contains("<qcom:propertyValue>ERP3_1</qcom:propertyValue>");

        // Validar Cuerpo
        assertThat(envelope).contains("<bcreditor>");
        assertThat(envelope).contains("<BCreditor");
    }
}