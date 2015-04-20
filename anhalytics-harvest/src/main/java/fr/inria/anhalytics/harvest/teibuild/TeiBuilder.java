package fr.inria.anhalytics.harvest.teibuild;

import fr.inria.anhalytics.commons.utilities.Utilities;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Attr;

public class TeiBuilder {

    public static String generateTeiCorpus(InputStream additionalTei, InputStream grobidTei, boolean modeBrutal) throws ParserConfigurationException, IOException {
        String teiString;
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

        
        /////////////// Hal specific : To be done as a harvesting post process before storing tei ////////////////////
        // remove ugly end-of-line in starting and ending text as it is
        // a problem for stand-off annotations
        Utilities.trimEOL(docAdditionalTei.getDocumentElement(), docAdditionalTei);
        docAdditionalTei = removeElement(docAdditionalTei, "analytic");
        NodeList orgs = docAdditionalTei.getElementsByTagName("org");
        NodeList authors = docAdditionalTei.getElementsByTagName("author");
        updateAffiliations(authors, orgs, docAdditionalTei);
        NodeList editors = docAdditionalTei.getElementsByTagName("editor");
        updateAffiliations(editors, orgs, docAdditionalTei);        
        ///////////////////////////////////////////////////////////////////////
        
        
        NodeList biblFull = docAdditionalTei.getElementsByTagName("biblFull");
        if (modeBrutal) {
            teiString = updateFullTextTeiBrutal(biblFull, grobidTei);// TBR
        } else {
            Document doc = null;
            try {
                doc = docBuilder.parse(grobidTei);
                createTEICorpus(doc);
            } catch (SAXException e) {
                e.printStackTrace();
            }
            teiString = updateCorpusTei(doc, biblFull);
        }
        return teiString;
    }

    private static Document removeElement(Document doc, String elementTagName) {
        Element element = (Element) doc.getElementsByTagName(elementTagName).item(0);
        element.getParentNode().removeChild(element);
        doc.normalize();
        return doc;
    }

    private static Node findNode(String id, NodeList orgs) {
        Node org = null;
        for (int i = 0; i < orgs.getLength(); i++) {
            NamedNodeMap attr = orgs.item(i).getAttributes();
            if (attr.getNamedItem("xml:id") == null) {
                continue;
            }
            if (attr.getNamedItem("xml:id").getNodeValue().equals(id)) {
                org = orgs.item(i);
                break;
            }
        }
        return org;
    }

    private static void updateAffiliations(NodeList persons, NodeList orgs, Document docAdditionalTei) {
        Node person = null;
        NodeList theNodes = null;
        for (int i = 0; i < persons.getLength(); i++) {
            person = persons.item(i);
            theNodes = person.getChildNodes();
            for (int y = 0; y < theNodes.getLength(); y++) {
                if (theNodes.item(y).getNodeType() == Node.ELEMENT_NODE) {
                    Element e = (Element) (theNodes.item(y));
                    if (e.getTagName().equals("affiliation")) {
                        String name = e.getAttribute("ref").replace("#", "");
                        Node aff = findNode(name, orgs);
                        if (aff != null) {
                            //person.removeChild(theNodes.item(y));
                            Node localNode = docAdditionalTei.importNode(aff, true);
                            // we need to rename this attribute because we cannot multiply the id attribute
                            // with the same value (XML doc becomes not well-formed)
                            Element orgElement = (Element) localNode;
                            orgElement.removeAttribute("xml:id");
                            orgElement.setAttribute("ref", "#" + name);
                            e.removeAttribute("ref");
                            e.appendChild(localNode);
                        }
                    }
                }
            }
        }
    }

    private static String updateCorpusTei(Document doc, NodeList biblFull) {
        Element teiHeader = doc.createElement("teiHeader");
        addAdditionalTeiHeader(biblFull, teiHeader, doc);
        doc.getLastChild().appendChild(teiHeader);
        return Utilities.toString(doc);
    }

    private static String updateFullTextTeiBrutal(NodeList biblFull, InputStream tei) throws IOException {
        String teiStr = IOUtils.toString(tei, "UTF-8");
        int ind1 = teiStr.indexOf("<teiHeader");
        int ind12 = teiStr.indexOf(">", ind1 + 1);
        int ind2 = teiStr.indexOf("</teiHeader>");
        teiStr = teiStr.substring(0, ind12 + 1) + Utilities.innerXmlToString(biblFull.item(0)) + teiStr.substring(ind2, teiStr.length());
        return teiStr;
    }

    private static void clear(Node node) {
        for (int i = node.getChildNodes().getLength() - 1; i >= 0; i--) {
            node.removeChild(node.getChildNodes().item(i));
        }
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
}
