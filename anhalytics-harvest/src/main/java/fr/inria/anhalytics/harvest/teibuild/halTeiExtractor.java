package fr.inria.anhalytics.harvest.teibuild;

import fr.inria.anhalytics.commons.utilities.Utilities;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Hal specific tei extraction and update.
 *
 * @author achraf
 */
public class halTeiExtractor {

    public static final String TITLE_PATH = "/TEI/text/body/listBibl/biblFull/titleStmt/title";
    public static final String ABSTRACT_PATH = "/TEI/text/body/listBibl/biblFull/profileDesc/abstract";
    public static final String AUTHORS_PATH = "/TEI/text/body/listBibl/biblFull/titleStmt/author";
    public static final String TEXTCLASS_PATH = "/TEI/text/body/listBibl/biblFull/profileDesc/textClass";
    
    public static NodeList getTeiHeader(Document docAdditionalTei) {
        /////////////// Hal specific : To be done as a harvesting post process before storing tei ////////////////////
        // remove ugly end-of-line in starting and ending text as it is
        // a problem for stand-off annotations
        Utilities.trimEOL(docAdditionalTei.getDocumentElement(), docAdditionalTei);
        docAdditionalTei = Utilities.removeElement(docAdditionalTei, "analytic");
        NodeList orgs = docAdditionalTei.getElementsByTagName("org");
        NodeList authors = docAdditionalTei.getElementsByTagName("author");
        updateAffiliations(authors, orgs, docAdditionalTei);
        NodeList editors = docAdditionalTei.getElementsByTagName("editor");
        updateAffiliations(editors, orgs, docAdditionalTei);
        return docAdditionalTei.getElementsByTagName("biblFull");
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
                        Node aff = Utilities.findNode(name, orgs);
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

    public static Node getTitle(Document document) {
        Node titleNode = null;
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            titleNode = (Node) xPath.evaluate(TITLE_PATH,
                    document.getDocumentElement(), XPathConstants.NODE);
        } catch (XPathExpressionException xpee) {
            xpee.printStackTrace();
        }
        return titleNode;
    }
    
    public static Node getAbstract(Document document) {
        Node abstractNode = null;
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            abstractNode = (Node) xPath.evaluate(ABSTRACT_PATH,
                    document.getDocumentElement(), XPathConstants.NODE);
        } catch (XPathExpressionException xpee) {
            xpee.printStackTrace();
        }
        return abstractNode;
    }
    
    public static NodeList getAuthors(Document document) {
        NodeList authorsNode = null;
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            authorsNode = (NodeList) xPath.evaluate(AUTHORS_PATH,
                    document.getDocumentElement(), XPathConstants.NODESET);
        } catch (XPathExpressionException xpee) {
            xpee.printStackTrace();
        }
        return authorsNode;
    }
    
    public static NodeList getTextClass(Document document) {
        NodeList textClassNode = null;
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            textClassNode = (NodeList) xPath.evaluate(TEXTCLASS_PATH,
                    document.getDocumentElement(), XPathConstants.NODESET);
        } catch (XPathExpressionException xpee) {
            //xpee.printStackTrace();
        }
        return textClassNode;
    }
}
