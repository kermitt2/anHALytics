package fr.inria.hal;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.apache.commons.io.IOUtils;

public class HalTeiAppender {

    public static String replaceHeader(InputStream halTei, InputStream grobidTei, boolean modeBrutal) throws ParserConfigurationException, SAXException, IOException, TransformerConfigurationException, TransformerException {
        String teiString;
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
        //docFactory.setNamespaceAware(true);

        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document docHalTei = docBuilder.parse(halTei);

        // add random xml:id on textual elements
        Utilities.generateIDs(docHalTei);

		// remove ugly end-of-line in starting and ending text as it is
        // a problem for stand-off annotations
        Utilities.trimEOL(docHalTei.getDocumentElement(), docHalTei);

        NodeList orgs = docHalTei.getElementsByTagName("org");
        NodeList authors = docHalTei.getElementsByTagName("author");
        updateAffiliations(authors, orgs, docHalTei);
        NodeList editors = docHalTei.getElementsByTagName("editor");
        updateAffiliations(editors, orgs, docHalTei);
        NodeList biblFull = docHalTei.getElementsByTagName("biblFull");

        if (modeBrutal) {
            teiString = updateFullTextTeiBrutal(biblFull, grobidTei);
        } else {
            Document doc = docBuilder.parse(grobidTei);
            teiString = updateFullTextTei(doc, biblFull);
        }
        return teiString;
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

    private static void updateAffiliations(NodeList persons, NodeList orgs, Document docHalTei) {
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
                            Node localNode = docHalTei.importNode(aff, true);
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

    private static String updateFullTextTei(Document doc, NodeList biblFull) {
        Node teiHeader = doc.getElementsByTagName("teiHeader").item(0);
        clear(teiHeader);
        addHalHeader(biblFull, teiHeader, doc);
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

    private static void addHalHeader(NodeList biblFull, Node header, Document doc) {
        if (biblFull.getLength() == 0) {
            return;
        }
        Node biblFullRoot = biblFull.item(0);
        for (int i = 0; i < biblFullRoot.getChildNodes().getLength(); i++) {
            Node localNode = doc.importNode(biblFullRoot.getChildNodes().item(i), true);
            header.appendChild(localNode);
        }
    }
}