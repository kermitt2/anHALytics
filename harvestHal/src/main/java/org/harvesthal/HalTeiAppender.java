
package org.harvesthal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HalTeiAppender {

    public static String replaceHeader(InputStream halTei, InputStream tei) throws ParserConfigurationException, SAXException, IOException, TransformerConfigurationException, TransformerException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        //docFactory.setValidating(false);
        //docFactory.setNamespaceAware(true);

        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();  
        Document doc = docBuilder.parse(tei);
        Document docHalTei = docBuilder.parse(halTei);
        NodeList orgs = docHalTei.getElementsByTagName("listOrg");
        NodeList authors = docHalTei.getElementsByTagName("author");
        updateAffiliations(authors, orgs);
        NodeList editors = docHalTei.getElementsByTagName("editor");
        updateAffiliations(editors, orgs);
        NodeList biblFull = docHalTei.getElementsByTagName("biblFull");
        updateFullTextTei(doc, biblFull);
        return toString(doc);
    }

    private static Node findNode(String id, NodeList orgs) {
        Node org = null;
        for (int i = 0; i < orgs.getLength(); i++) {
            NamedNodeMap attr = orgs.item(i).getAttributes();
            if (id.equals(attr.getNamedItem("id").getNodeName())) {
                org = orgs.item(i);
            }
            break;
        }
        return org;
    }

    private static void updateAffiliations(NodeList persons, NodeList orgs) {
        Node person = null;
        NodeList nodes = null;
        NamedNodeMap attr = null;
        for (int i = 0; i < persons.getLength(); i++) {
            person = persons.item(i);
            nodes = person.getChildNodes();
            for (int y = 0; y < nodes.getLength(); y++) {
                if ("affiliation".equals(nodes.item(i).getNodeName())) {
                    attr = nodes.item(i).getAttributes();
                    Node aff = findNode(attr.getNamedItem("ref").getNodeName().replace("#", ""), orgs);
                    person.removeChild(nodes.item(i));
                    person.appendChild(aff);
                }
            }
        }
    }

    private static void updateFullTextTei(Document doc, NodeList biblFull) {
        Node teiHeader = doc.getElementsByTagName("teiHeader").item(0);
        clear(teiHeader);
        addHalHeader(biblFull, teiHeader);

    }

    private static void clear(Node node) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            node.removeChild(node.getChildNodes().item(i));
        }
    }

    private static void addHalHeader(NodeList biblFull, Node header) {
        for (int i = 0; i < biblFull.getLength(); i++) {
            header.appendChild(biblFull.item(i));
        }

    }
    
    private static String toString(Document doc) {
    try {
        StringWriter sw = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    } catch (Exception ex) {
        throw new RuntimeException("Error converting to String", ex);
    }
}
}
