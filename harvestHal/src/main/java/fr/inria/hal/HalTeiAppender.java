
package fr.inria.hal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
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
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.ls.LSSerializer;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
	

public class HalTeiAppender {
    
    public static String replaceHeader(InputStream halTei, InputStream grobidTei, boolean modeBrutal) throws ParserConfigurationException, SAXException, IOException, TransformerConfigurationException, TransformerException {
        String teiString;
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
        //docFactory.setNamespaceAware(true);

        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document docHalTei = docBuilder.parse(halTei);
		
		// add random xml:id on textual elements
		XmlFormatter.generateIDs(docHalTei);
		
		// remove ugly end-of-line in starting and ending text as it is
		// a problem for stand-off annotations
		XmlFormatter.trimEOL(docHalTei.getDocumentElement(), docHalTei);

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
			if (attr.getNamedItem("xml:id") == null)
				continue;
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
        NamedNodeMap attr = null;
        for (int i = 0; i < persons.getLength(); i++) {
            person = persons.item(i);		
			theNodes = person.getChildNodes();
            for (int y = 0; y < theNodes.getLength(); y++) {
				if (theNodes.item(y).getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element)(theNodes.item(y));
					if (e.getTagName().equals("affiliation")) {	
						String name = e.getAttribute("ref").replace("#", "");
	                    Node aff = findNode(name, orgs);
						if (aff != null) {
							//person.removeChild(theNodes.item(y));
							Node localNode = docHalTei.importNode(aff, true); 
							// we need to rename this attribute because we cannot multiply the id attribute
							// with the same value (XML doc becomes not well-formed)
							Element orgElement = (Element)localNode;
							orgElement.removeAttribute("xml:id");
							orgElement.setAttribute("ref", "#"+name);
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
        return toString(doc);
    }
    
    
    private static String updateFullTextTeiBrutal(NodeList biblFull, InputStream tei) throws IOException {
        String teiStr = IOUtils.toString(tei, "UTF-8");
        int ind1 = teiStr.indexOf("<teiHeader");
        int ind12 = teiStr.indexOf(">", ind1+1);
        int ind2 = teiStr.indexOf("</teiHeader>");
         
        teiStr = teiStr.substring(0, ind12+1) + innerXmlToString(biblFull.item(0)) + teiStr.substring(ind2, teiStr.length());
        return teiStr;
    }

    private static void clear(Node node) {
        for (int i = node.getChildNodes().getLength()-1; i >=0 ; i--) {
            node.removeChild(node.getChildNodes().item(i));
        }
    }

    private static void addHalHeader(NodeList biblFull, Node header, Document doc) {
		if (biblFull.getLength() == 0)
			return;
		Node biblFullRoot = biblFull.item(0);
        for (int i = 0; i < biblFullRoot.getChildNodes().getLength(); i++) {
			Node localNode = doc.importNode(biblFullRoot.getChildNodes().item(i), true); 
            header.appendChild(localNode);
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
	
	public static String innerXmlToString(Node node) {
	    DOMImplementationLS lsImpl = 
			(DOMImplementationLS)node.getOwnerDocument().getImplementation().getFeature("LS", "3.0");
	    LSSerializer lsSerializer = lsImpl.createLSSerializer();
		lsSerializer.getDomConfig().setParameter("xml-declaration", false);
	    NodeList childNodes = node.getChildNodes();
	    StringBuilder sb = new StringBuilder();
	    for (int i = 0; i < childNodes.getLength(); i++) {
	       sb.append(lsSerializer.writeToString(childNodes.item(i)));
	    }
	    return sb.toString(); 
	}
}
