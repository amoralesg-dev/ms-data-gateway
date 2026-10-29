package com.gateway.qad.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Utilería robusta para creación, manipulación y serialización de árboles XML DOM
 * conforme a los estándares de W3C y seguro contra inyecciones y ataques XXE.
 */
public final class XmlDocumentHelper {

    private XmlDocumentHelper() {}

    public static Document createDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Error al inicializar DocumentBuilder seguro", e);
        }
    }

    public static Element appendElement(Element parent, String tagName, String textContent) {
        Document doc = parent.getOwnerDocument();
        Element el = doc.createElement(tagName);
        if (textContent != null) {
            el.setTextContent(textContent);
        }
        parent.appendChild(el);
        return el;
    }

    public static Element appendElementNS(Element parent, String namespaceUri, String qualifiedName, String textContent) {
        Document doc = parent.getOwnerDocument();
        Element el = doc.createElementNS(namespaceUri, qualifiedName);
        if (textContent != null) {
            el.setTextContent(textContent);
        }
        parent.appendChild(el);
        return el;
    }

    public static String serializeToString(Document doc, boolean omitXmlDeclaration) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            if (omitXmlDeclaration) {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Error al serializar Document a XML", e);
        }
    }

    public static String serializeElementToString(Element element) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Error al serializar Element a XML", e);
        }
    }
}