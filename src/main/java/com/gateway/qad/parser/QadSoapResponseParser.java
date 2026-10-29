package com.gateway.qad.parser;

import com.gateway.qad.dto.QadServiceResponse;
import com.gateway.qad.dto.QadServiceResponse.QadExceptionDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

/**
 * Parser de respuestas SOAP provenientes de QAD QXtend.
 * Interpreta:
 * 1. Success: <result>success</result>
 * 2. Error funcional: <result>error</result> y extrae <dsExceptions> / <temp_err_msg>
 * 3. Fallas técnicas SOAP: <soapenv:Fault> o <faultstring>
 */
@Slf4j
@Component
public class QadSoapResponseParser {

    public QadServiceResponse parse(String xmlResponse, long executionTimeMs) {
        if (xmlResponse == null || xmlResponse.isBlank()) {
            return QadServiceResponse.technicalError("EMPTY_RESPONSE", "QAD returned an empty response", executionTimeMs);
        }

        try {
            Document doc = parseXml(xmlResponse);

            // 1. Detectar SOAP Fault (<Fault> o <soapenv:Fault>)
            Element faultElem = findFirstElement(doc, "Fault");
            if (faultElem != null) {
                String faultCode = getChildText(faultElem, "faultcode");
                String faultString = getChildText(faultElem, "faultstring");
                if (faultString == null || faultString.isBlank()) {
                    faultString = "QAD SOAP Fault occurred";
                }
                log.warn("QAD SOAP Fault detectado: faultcode={}, faultstring={}", faultCode, faultString);
                return QadServiceResponse.technicalError("SOAP_FAULT", faultString, executionTimeMs);
            }

            // 2. Extraer metadatos de sesión (dsSessionContext devuelto en la respuesta)
            Map<String, Object> sessionContext = extractSessionContext(doc);

            // 3. Evaluar <result>
            String resultText = getElementText(doc, "result");
            if (resultText == null || resultText.isBlank()) {
                // Si no hay <result>, buscar si existe algún elemento raíz de respuesta (ej. *Response)
                Element body = findFirstElement(doc, "Body");
                if (body != null && body.hasChildNodes()) {
                    NodeList children = body.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Node.ELEMENT_NODE && child.getLocalName() != null && child.getLocalName().endsWith("Response")) {
                            resultText = "success";
                            break;
                        }
                    }
                }
            }


            if ("success".equalsIgnoreCase(resultText)) {
                log.info("QAD Operación completada exitosamente en {} ms", executionTimeMs);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("result", "success");
                data.put("status", "COMPLETED");

                return QadServiceResponse.success("Operación procesada exitosamente en QAD", data, sessionContext, executionTimeMs);
            }

            // 4. Evaluar Error Funcional (<result>error</result> o presencia de <dsExceptions>)
            List<QadExceptionDetail> exceptions = extractExceptions(doc);
            if (!exceptions.isEmpty() || "error".equalsIgnoreCase(resultText)) {
                String primaryCode = "QAD_FUNCTIONAL_ERROR";
                String primaryMessage = "Error de validación o lógica de negocio en QAD";

                if (!exceptions.isEmpty()) {
                    QadExceptionDetail first = exceptions.get(0);
                    if (first.getMessageNumber() != null && !first.getMessageNumber().isBlank()) {
                        primaryCode = first.getMessageNumber();
                    }
                    if (first.getMessageDesc() != null && !first.getMessageDesc().isBlank()) {
                        primaryMessage = first.getMessageDesc();
                        if (first.getMessageData() != null && !first.getMessageData().isBlank()) {
                            primaryMessage += " - " + first.getMessageData();
                        }
                    }
                }

                log.warn("QAD Error funcional detectado: code={}, message={}", primaryCode, primaryMessage);
                return QadServiceResponse.functionalError(primaryCode, primaryMessage, exceptions, executionTimeMs);
            }

            // Si no coincide con ningún patrón conocido pero el XML es válido
            return QadServiceResponse.success("Respuesta recibida de QAD", Map.of("raw", xmlResponse), sessionContext, executionTimeMs);

        } catch (Exception e) {
            log.error("Error analizando XML de respuesta de QAD: {}", e.getMessage(), e);
            return QadServiceResponse.technicalError("XML_PARSE_ERROR", "Error analizando respuesta SOAP: " + e.getMessage(), executionTimeMs);
        }
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Prevenir ataques XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private List<QadExceptionDetail> extractExceptions(Document doc) {
        List<QadExceptionDetail> list = new ArrayList<>();
        NodeList errNodes = doc.getElementsByTagNameNS("*", "temp_err_msg");
        if (errNodes.getLength() == 0) {
            errNodes = doc.getElementsByTagName("temp_err_msg");
        }

        for (int i = 0; i < errNodes.getLength(); i++) {
            Node node = errNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String nbr = getChildText(el, "tt_msg_nbr");
                String desc = getChildText(el, "tt_msg_desc");
                String field = getChildText(el, "tt_msg_field");
                String data = getChildText(el, "tt_msg_data");
                String sev = getChildText(el, "tt_msg_sev");
                String context = getChildText(el, "tt_msg_context");
                String lvlStr = getChildText(el, "tt_level");

                Integer level = null;
                if (lvlStr != null && !lvlStr.isBlank()) {
                    try {
                        level = Integer.parseInt(lvlStr.trim());
                    } catch (NumberFormatException ignored) {}
                }

                list.add(QadExceptionDetail.builder()
                        .messageNumber(nbr)
                        .messageDesc(desc)
                        .messageField(field)
                        .messageData(data)
                        .messageSev(sev)
                        .messageContext(context)
                        .level(level)
                        .build());
            }
        }
        return list;
    }

    private Map<String, Object> extractSessionContext(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        NodeList ttContextNodes = doc.getElementsByTagNameNS("*", "ttContext");
        if (ttContextNodes.getLength() == 0) {
            ttContextNodes = doc.getElementsByTagName("ttContext");
        }

        for (int i = 0; i < ttContextNodes.getLength(); i++) {
            Node node = ttContextNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String propName = getChildText(el, "propertyName");
                String propVal = getChildText(el, "propertyValue");
                if (propName != null && !propName.isBlank()) {
                    map.put(propName, propVal != null ? propVal : "");
                }
            }
        }
        return map;
    }

    private Element findFirstElement(Document doc, String localName) {
        NodeList list = doc.getElementsByTagNameNS("*", localName);
        if (list.getLength() > 0) {
            return (Element) list.item(0);
        }
        list = doc.getElementsByTagName(localName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private String getElementText(Document doc, String localName) {
        Element el = findFirstElement(doc, localName);
        return el != null ? el.getTextContent().trim() : null;
    }

    private String getChildText(Element parent, String childName) {
        NodeList list = parent.getElementsByTagNameNS("*", childName);
        if (list.getLength() == 0) {
            list = parent.getElementsByTagName(childName);
        }
        if (list.getLength() > 0) {
            return list.item(0).getTextContent().trim();
        }
        return null;
    }
}

