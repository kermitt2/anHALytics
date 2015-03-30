package fr.inria.hal.annotation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 *
 */
public class AnnotatorWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(AnnotatorWorker.class);
    private MongoManager mm = null;
    private String filename = null;
    private String tei = null;
    private String halID = null;
    private String nerd_host = null;
    private String nerd_port = null;

    public AnnotatorWorker(MongoManager mongoManager,
            String filename,
            String halID,
            String tei,
            String nerd_host,
            String nerd_port) {
        this.mm = mongoManager;
        this.filename = filename;
        this.halID = halID;
        this.tei = tei;
        this.nerd_host = nerd_host;
        this.nerd_port = nerd_port;
    }

    @Override
    public void run() {
        try {
            long startTime = System.nanoTime();
            System.out.println(Thread.currentThread().getName() + " Start. Processing = " + filename);
            processCommand();
            long endTime = System.nanoTime();
            System.out.println(Thread.currentThread().getName() + " End. :" + (endTime - startTime) / 1000000 + " ms");
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(AnnotatorWorker.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void processCommand() throws IOException {
        List<String> halDomainTexts = new ArrayList<String>();
        List<String> halDomains = new ArrayList<String>();
        List<String> meSHDescriptors = new ArrayList<String>();
        try {
            // DocumentBuilderFactory and DocumentBuilder are not thread safe, 
            // so one per task
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // parse the TEI
            Document docTei
                    = docBuilder.parse(new InputSource(new ByteArrayInputStream(tei.getBytes("utf-8"))));

            // get the HAL domain 
            NodeList classes = docTei.getElementsByTagName("classCode");
            for (int p = 0; p < classes.getLength(); p++) {
                Node node = classes.item(p);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element e = (Element) (node);
                    // filter on attribute @scheme="halDomain"
                    String scheme = e.getAttribute("scheme");
                    if ((scheme != null) && scheme.equals("halDomain")) {
                        halDomainTexts.add(e.getTextContent());
                        String n_att = e.getAttribute("n");
                        halDomains.add(n_att);
                    } else if ((scheme != null) && scheme.equals("mesh")) {
                        meSHDescriptors.add(e.getTextContent());
                    }
                }
            }
            // get all the elements having an attribute id and annotate their text content
            String jsonAnnotations = annotateDocument(docTei, filename, halID, nerd_host, nerd_port);
            mm.insertAnnotation(jsonAnnotations);
        } catch (RuntimeException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.debug("\t\t " + filename + " annotated.");
    }

    public String annotateDocument(Document doc,
            String filename,
            String halID) {
        return annotateDocument(doc, filename, halID, nerd_host, nerd_port);
    }

    /**
     * Annotation of a complete document
     */
    public static String annotateDocument(Document doc,
            String filename,
            String halID,
            String nerd_host,
            String nerd_port) {
        StringBuffer json = new StringBuffer();
        json.append("{ \"filename\" : \"" + filename
                + "\", \"halID\" : \"" + halID
                + "\", \"nerd\" : [");
        annotateNode(doc.getDocumentElement(), true, json, nerd_host, nerd_port);
        json.append("] }");
        return json.toString();
    }

    /**
     * Recursive tree walk for annotating every nodes having a random xml:id
     */
    public static boolean annotateNode(Node node,
            boolean first,
            StringBuffer json,
            String nerd_host,
            String nerd_port) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element e = (Element) (node);
            String id = e.getAttribute("xml:id");
            if (id.startsWith("_") && (id.length() == 8)) {
                // get the textual content of the element
                // annotate
                String text = e.getTextContent();
                String jsonText = null;
                try {
                    NerdService nerdService = new NerdService(text, nerd_host, nerd_port);
                    jsonText = nerdService.runNerd();
                } catch (Exception ex) {
                    logger.debug("Text could not be annotated by NERD: " + text);
                    ex.printStackTrace();
                }
                if (jsonText != null) {
                    // resulting annotations, with the corresponding id
                    if (first) {
                        first = false;
                    } else {
                        json.append(", ");
                    }
                    json.append("{ \"xml:id\" : \"" + id + "\", \"nerd\" : " + jsonText + " }");
                }
            }
        }
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            first = annotateNode(currentNode, first, json, nerd_host, nerd_port);
        }
        return first;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public String toString() {
        return this.filename;
    }
}
