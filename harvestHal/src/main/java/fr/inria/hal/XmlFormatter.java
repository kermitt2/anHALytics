package fr.inria.hal;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.grobid.core.utilities.KeyGen;


/**
 * Pretty-prints xml, supplied as a string.
 */
public class XmlFormatter {

    public XmlFormatter() {
    }
	
	/**
	 *  Add random xml ids on the textual nodes of the document 
	 */
	public static void generateIDs(Document doc) {
        NodeList titles = doc.getElementsByTagName("title");
        NodeList abstracts = doc.getElementsByTagName("abstract");
		NodeList terms = doc.getElementsByTagName("term");
		NodeList funders = doc.getElementsByTagName("funder");
		NodeList codes = doc.getElementsByTagName("classCode");
		generateID(titles);
		generateID(abstracts);
		generateID(terms);
		generateID(funders);
		generateID(codes);
	}
	
	private static void generateID(NodeList theNodes) {
        for (int i = 0; i < theNodes.getLength(); i++) {
            Element theElement = (Element)theNodes.item(i);	
			String divID = KeyGen.getKey().substring(0,7);
			theElement.setAttribute("xml:id", "_" + divID);
		}
	}
	
	/**
	 *  Remove starting and ending end-of-line in XML element text content recursively
	 */
	public static void trimEOL(Node node, Document doc) {
		if (node.getNodeType() == Node.TEXT_NODE) {
			String text = node.getNodeValue();
			
			if (text.replaceAll("[ \\t\\r\\n]+", "").length() != 0) {
				while (text.startsWith("\n") && text.length()>0) {
					text = text.substring(1,text.length());
				}
				while (text.endsWith("\n") && text.length()>0) {
					text = text.substring(0,text.length()-1);
				}
				node.setNodeValue(text);
			}
		}
		NodeList nodeList = node.getChildNodes();
	    for (int i = 0; i < nodeList.getLength(); i++) {
	        Node currentNode = nodeList.item(i);
	        //if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
	            trimEOL(currentNode, doc);
			//}
	    }
	}

    public String format(String unformattedXml) {
        try {
            final Document document = parseXmlFile(unformattedXml);

            OutputFormat format = new OutputFormat(document);
            format.setLineWidth(65);
            format.setIndenting(true);
            format.setIndent(2);
            Writer out = new StringWriter();
            XMLSerializer serializer = new XMLSerializer(out, format);
            serializer.serialize(document);
            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Document parseXmlFile(String in) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(in));
            return db.parse(is);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
	
}
