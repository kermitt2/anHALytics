package fr.inria.anhalytics.harvest.teibuild;

import fr.inria.anhalytics.commons.utilities.Utilities;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Attr;

public class TeiBuilder {

    public static String generateTeiCorpus(InputStream additionalTei, InputStream grobidTei, boolean update) throws ParserConfigurationException, IOException {
        String teiString = null;
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
        //docFactory.setNamespaceAware(true);

        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document docAdditionalTei = null;
        try {
            docAdditionalTei = docBuilder.parse(additionalTei);
        } catch (SAXException e) {
            e.printStackTrace();
        }
        // add random xml:id on textual elements
        Utilities.generateIDs(docAdditionalTei);

        NodeList teiHeader = halTeiExtractor.getTeiHeader(docAdditionalTei);

        Document doc = null;
        try {
            doc = docBuilder.parse(grobidTei);

            //if update activated, title, abstract, authors and textclass will be update using available metadata.
            if (update) {
                updateGrobidTEI(doc, docAdditionalTei);
            }
            createTEICorpus(doc);
            teiString = updateCorpusTei(doc, teiHeader);
        } catch (SAXException e) {
            e.printStackTrace();
        }
        return teiString;
    }

    private static String updateCorpusTei(Document doc, NodeList biblFull) {
        Element teiHeader = doc.createElement("teiHeader");
        addAdditionalTeiHeader(biblFull, teiHeader, doc);
        doc.getLastChild().appendChild(teiHeader);
        return Utilities.toString(doc);
    }

    private static void addAdditionalTeiHeader(NodeList biblFull, Node header, Document doc) {
        if (biblFull.getLength() == 0) {
            return;
        }
        Node biblFullRoot = biblFull.item(0);
        for (int i = 0; i < biblFullRoot.getChildNodes().getLength(); i++) {
            Node localNode = doc.importNode(biblFullRoot.getChildNodes().item(i), true);
            header.appendChild(localNode);
        }
    }

    private static void createTEICorpus(Document doc) {
        Element tei = (Element) doc.getDocumentElement();
        Attr attr = tei.getAttributeNode("xmlns");
        tei.removeAttributeNode(attr);
        Element teiCorpus = doc.createElement("teiCorpus");
        teiCorpus.appendChild(tei);
        teiCorpus.setAttributeNode(attr);
        doc.appendChild(teiCorpus);
    }

    /**
     * Updates the extracted TEI with the authors data.
     */
    private static void updateGrobidTEI(Document doc, Document docAdditionalTei) {
        Node titleNode = halTeiExtractor.getTitle(docAdditionalTei);
        Node abstractNode = halTeiExtractor.getAbstract(docAdditionalTei);
        NodeList authorsNodes = halTeiExtractor.getAuthors(docAdditionalTei);
        NodeList textClassNode = halTeiExtractor.getTextClass(docAdditionalTei);

        ((Element) doc.getElementsByTagName("title").item(0)).setTextContent(titleNode.getTextContent());
        Element abstractTextNode = ((Element) getAbstractTextNode(doc));
        if (abstractTextNode != null) {
            abstractTextNode.setTextContent(abstractNode.getTextContent());
        }
        Node authsNode = getAuthorsNode(doc);
        for (int i = 0; i < authorsNodes.getLength(); i++) {
            Node localNode = doc.importNode(authorsNodes.item(i), true);
            authsNode.appendChild(localNode);
        }

        Node textClass = getTextClass(doc);
        Node myNode = doc.importNode(textClassNode.item(0), true);
        if (textClass == null) {
            Element profileDesc = doc.createElement("profileDesc");
            profileDesc.appendChild(myNode);
            getTeiHeader(doc).appendChild(profileDesc);
        } else {
            for (int i = myNode.getChildNodes().getLength() - 1; i >= 0; i--) {
                textClass.appendChild(myNode.getChildNodes().item(i));
            }
        }
    }

    private static Node getAbstractTextNode(Document doc) {
        Node AbstractTextNode = null;
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            AbstractTextNode = (Node) xPath.evaluate("/TEI/teiHeader/profileDesc/abstract/p",
                    doc.getDocumentElement(), XPathConstants.NODE);
        } catch (XPathExpressionException xpee) {
            //xpee.printStackTrace();
        }
        return AbstractTextNode;
    }

    private static Node getAuthorsNode(Document doc) {
        Node authorsNode = null;
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            authorsNode = (Node) xPath.evaluate("/TEI/teiHeader/fileDesc/sourceDesc/biblStruct/analytic",
                    doc.getDocumentElement(), XPathConstants.NODE);
        } catch (XPathExpressionException xpee) {
            //xpee.printStackTrace();
        }
        return authorsNode;
    }

    private static Node getTextClass(Document doc) {
        Node textClassNode = null;
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            textClassNode = (Node) xPath.evaluate("/TEI/teiHeader/profileDesc/textClass",
                    doc.getDocumentElement(), XPathConstants.NODE);
        } catch (XPathExpressionException xpee) {
        }
        return textClassNode;
    }

    private static Node getTeiHeader(Document doc) {
        Node teiHeaderNode = null;
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            teiHeaderNode = (Node) xPath.evaluate("/TEI/teiHeader",
                    doc.getDocumentElement(), XPathConstants.NODE);
        } catch (XPathExpressionException xpee) {
            //
        }
        return teiHeaderNode;
    }
}
