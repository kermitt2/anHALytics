package fr.inria.anhalytics.harvest;

import fr.inria.anhalytics.commons.data.TEI;
import java.net.URLEncoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

/**
 *
 * @author Achraf
 */
public class OAIPMHDom implements OAIPMHMetadata {

    private List<TEI> teis = new ArrayList<TEI>();
    private Document doc;
    private String token;
    private XPath xPath;

    public OAIPMHDom() {
        xPath = XPathFactory.newInstance().newXPath();
    }

    public List<TEI> getTeis(InputStream in) throws SAXException, IOException, ParserConfigurationException {
        teis = new ArrayList<TEI>();
        setDoc(parse(in));
        Element rootElement = doc.getDocumentElement();
        NodeList listRecords = getRecords(rootElement);

        //XPath xPath = XPathFactory.newInstance().newXPath();//play with it  
        //NodeList nodes = (NodeList)xPath.evaluate("/OAI-PMH/ListRecords/record/metadata", rootElement, XPathConstants.NODESET);
        setToken(rootElement);
        if (listRecords.getLength() >= 1) {
            for (int i = listRecords.getLength() - 1; i >= 0; i--) {
                if ((listRecords.item(i) instanceof Element)) {
                    Element record = (Element) listRecords.item(i);
                    String type = getDocumentType(record.getElementsByTagName(TypeElement));
                    if (isConsideredType(type)) {
                        String tei = getTei(record.getElementsByTagName(TeiElement));
                        String doi = getDoi(record);
                        String id = getId(record.getElementsByTagName(IdElement));
                        String fileUrl = getFileUrl(record);
                        String ref = getRef(record);
                        teis.add(new TEI(id, tei, doi, type, fileUrl, ref));
                    }
                }
            }

        }
        return teis;
    }

    public String getRef(Node ref) {

        String reference = null;
        try {
            Node node = (Node) xPath.compile(RefPATH).evaluate(ref, XPathConstants.NODE);
            reference = node.getTextContent();
        } catch (Exception ex) {
            // REf is not always available !
        }
        return reference;
    }

    public String getDoi(Node ref) {
        String doi = null;
        try {
            Node node = (Node) xPath.compile(DoiPATH).evaluate(ref, XPathConstants.NODE);
            doi = node.getTextContent();
        } catch (Exception ex) {
            // DOI is not always available !
        }
        return doi;
    }

    public String getToken() {
        return this.token;
    }

    private void setToken(Element rootElement) {
        try {
            this.token = URLEncoder.encode(rootElement.getElementsByTagName(ResumptionToken).item(0).getTextContent());
        } catch (Exception ex) {
            this.token = null;
        }
    }

    private void setDoc(Document doc) {
        this.doc = doc;
    }

    private Document parse(InputStream in) throws SAXException, ParserConfigurationException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(in);
    }

    @Override
    public String getFileUrl(Node record) {
        String url = null;        
        try {
            Element node = (Element) xPath.compile(FileUrlElement).evaluate(record, XPathConstants.NODE);
            url = node.getAttribute("target");
        } catch (Exception ex) {
        }
        return url;
    }

    @Override
    public String getTei(NodeList tei) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        NodeList teiNodes = tei.item(0).getChildNodes();
        for (int i = teiNodes.getLength() - 1; i >= 0; i--) {
            if (teiNodes.item(i) instanceof Element) {
                Element teiElement = (Element) teiNodes.item(i);
                teiElement.setAttribute("xmlns:tei", "http://www.tei-c.org/ns/1.0");//BOF
                renameNode(teiElement);
            }
        }
        return sb.append(innerXmlToString(tei.item(0))).toString();
    }

    private void renameNode(Element teiElement) {
        doc.renameNode(teiElement, null, teiElement.getTagName().split(":")[1]);
        NodeList nodes = teiElement.getChildNodes();
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            if (nodes.item(i) instanceof Element) {
                Element element = (Element) nodes.item(i);
                renameNode(element);
            }
        }
    }

    @Override
    public String getId(NodeList identifier) {
        return identifier.item(0).getTextContent().split(":")[2];
    }

    @Override
    public String getDocumentType(NodeList sets) {
        String type = null;
        for (int i = sets.getLength() - 1; i >= 0; i--) {
            String content = sets.item(i).getTextContent();
            if (content.contains("type")) {
                String[] n = content.split(":");
                type = n.length > 1 ? n[1] : null;
                break;
            }
        }
        return type;
    }

    private NodeList getRecords(Element rootElement) {
        return rootElement.getElementsByTagName(RecordElement);
    }

    private static String innerXmlToString(Node node) {
        DOMImplementationLS lsImpl
                = (DOMImplementationLS) node.getOwnerDocument().getImplementation().getFeature("LS", "3.0");
        LSSerializer lsSerializer = lsImpl.createLSSerializer();
        lsSerializer.getDomConfig().setParameter("xml-declaration", false);
        NodeList childNodes = node.getChildNodes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < childNodes.getLength(); i++) {
            sb.append(lsSerializer.writeToString(childNodes.item(i)));
        }
        return sb.toString();
    }

    private boolean isConsideredType(String setSpec) {
        try {
            ConsideredTypes.valueOf(setSpec);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
    }
}
