package com.gateway.qad.envelope;

import com.gateway.qad.util.XmlDocumentHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Map;
import java.util.UUID;

import static com.gateway.qad.util.XmlDocumentHelper.appendElementNS;

/**
 * Constructor de Envelopes SOAP 1.1 para QAD QXtend basado en arquitectura DOM W3C.
 * Construye de forma estructurada y estricta:
 * - soapenv:Envelope y namespaces requeridos
 * - soapenv:Header con WS-Addressing (Action, To, MessageID, ReferenceParameters, ReplyTo)
 * - qcom:suppressResponseDetail en ReferenceParameters
 * - soapenv:Body conteniendo la operación de servicio
 * - qcom:dsSessionContext con namespace qcom obligatorio.
 */
@Slf4j
@Component
public class QadSoapEnvelopeBuilder {

    public static final String NS_SOAPENV = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String NS_QSVC = "urn:schemas-qad-com:xml-services";
    public static final String NS_QCOM = "urn:schemas-qad-com:xml-services:common";
    public static final String NS_WSA = "http://www.w3.org/2005/08/addressing";

    /**
     * Construye el sobre SOAP completo utilizando serialización DOM estandarizada.
     *
     * @param operationName Nombre de la operación (ej. bcreditor)
     * @param sessionContext Datos de la sesión QAD
     * @param businessPayload XML interno generado por el mapper (ej. <BCreditor>...</BCreditor>)
     * @return XML SOAP Envelope completo listo para envío HTTP
     */
    public String buildEnvelope(String operationName, QadSessionContext sessionContext, String businessPayload) {
        Document doc = XmlDocumentHelper.createDocument();

        // <soapenv:Envelope>
        Element envelope = doc.createElementNS(NS_SOAPENV, "soapenv:Envelope");
        envelope.setAttribute("xmlns", NS_QSVC);
        envelope.setAttribute("xmlns:qcom", NS_QCOM);
        envelope.setAttribute("xmlns:soapenv", NS_SOAPENV);
        envelope.setAttribute("xmlns:wsa", NS_WSA);
        doc.appendChild(envelope);

        // 1. Header WS-Addressing
        Element header = doc.createElementNS(NS_SOAPENV, "soapenv:Header");
        envelope.appendChild(header);

        appendElementNS(header, NS_WSA, "wsa:Action", "");
        appendElementNS(header, NS_WSA, "wsa:To", "urn:services-qad-com:QADERP");
        appendElementNS(header, NS_WSA, "wsa:MessageID", "urn:services-qad-com::QADERP:" + UUID.randomUUID());

        Element refParams = appendElementNS(header, NS_WSA, "wsa:ReferenceParameters", null);
        appendElementNS(refParams, NS_QCOM, "qcom:suppressResponseDetail", "true");

        Element replyTo = appendElementNS(header, NS_WSA, "wsa:ReplyTo", null);
        appendElementNS(replyTo, NS_WSA, "wsa:Address", "urn:services-qad-com:");

        // 2. Body con operación y contexto
        Element body = doc.createElementNS(NS_SOAPENV, "soapenv:Body");
        envelope.appendChild(body);

        Element operation = doc.createElementNS(NS_QSVC, operationName);
        body.appendChild(operation);

        // qcom:dsSessionContext
        Element dsSessionContext = appendElementNS(operation, NS_QCOM, "qcom:dsSessionContext", null);

        appendContextProperty(dsSessionContext, "domain", sessionContext.getDomain() != null ? sessionContext.getDomain() : "MXMGT01");
        appendContextProperty(dsSessionContext, "scopeTransaction", sessionContext.getScopeTransaction() != null ? sessionContext.getScopeTransaction() : "false");
        appendContextProperty(dsSessionContext, "version", sessionContext.getVersion() != null ? sessionContext.getVersion() : "ERP3_1");
        appendContextProperty(dsSessionContext, "mnemonicsRaw", sessionContext.getMnemonicsRaw() != null ? sessionContext.getMnemonicsRaw() : "false");
        appendContextProperty(dsSessionContext, "action", sessionContext.getAction() != null ? sessionContext.getAction() : "SAVE");
        appendContextProperty(dsSessionContext, "entity", sessionContext.getEntity() != null ? sessionContext.getEntity() : "0109");

        if (sessionContext.getUsername() != null && !sessionContext.getUsername().isBlank()) {
            appendContextProperty(dsSessionContext, "username", sessionContext.getUsername());
        }
        if (sessionContext.getPassword() != null && !sessionContext.getPassword().isBlank()) {
            appendContextProperty(dsSessionContext, "password", sessionContext.getPassword());
        }

        if (sessionContext.getAdditionalProperties() != null) {
            for (Map.Entry<String, String> entry : sessionContext.getAdditionalProperties().entrySet()) {
                appendContextProperty(dsSessionContext, entry.getKey(), entry.getValue());
            }
        }

        // 3. Importar payload de negocio al árbol DOM
        if (businessPayload != null && !businessPayload.isBlank()) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder db = factory.newDocumentBuilder();
                Document payloadDoc = db.parse(new InputSource(new StringReader(businessPayload)));
                Node importedPayload = doc.importNode(payloadDoc.getDocumentElement(), true);
                operation.appendChild(importedPayload);
            } catch (Exception e) {
                log.error("Error al importar businessPayload al DOM de Envelope: {}", e.getMessage(), e);
                throw new IllegalStateException("Error al parsear businessPayload en DOM", e);
            }
        }

        return XmlDocumentHelper.serializeToString(doc, false);
    }

    private void appendContextProperty(Element parent, String propName, String propValue) {
        Element ttContext = appendElementNS(parent, NS_QCOM, "qcom:ttContext", null);
        appendElementNS(ttContext, NS_QCOM, "qcom:propertyQualifier", "QAD");
        appendElementNS(ttContext, NS_QCOM, "qcom:propertyName", propName);
        appendElementNS(ttContext, NS_QCOM, "qcom:propertyValue", propValue != null ? propValue : "");
    }
}