package fr.inria.hal.annotation;

import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.net.*;

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
import org.xml.sax.InputSource;

import org.codehaus.jackson.*;
import org.codehaus.jackson.node.*;
import org.codehaus.jackson.map.ObjectMapper;

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
	        Document docTei = 
				docBuilder.parse(new InputSource(new ByteArrayInputStream(tei.getBytes("utf-8"))));

			// get the HAL domain 
			NodeList classes = docTei.getElementsByTagName("classCode");
			for(int p=0; p<classes.getLength(); p++) {
				Node node = classes.item(p);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element)(node);
					// filter on attribute @scheme="halDomain"
					String scheme = e.getAttribute("scheme");
					if ( (scheme != null) && scheme.equals("halDomain") ) {
						halDomainTexts.add(e.getTextContent());
						String n_att = e.getAttribute("n");
						halDomains.add(n_att);
					}
					else if ( (scheme != null) && scheme.equals("mesh") ) {
						meSHDescriptors.add(e.getTextContent());							
					}
				}
			}
			// get all the elements having an attribute id and annotate their text content
			String jsonAnnotations = Annotator.annotateDocument(docTei, filename, halID, nerd_host, nerd_port);
			mm.insertAnnotation(jsonAnnotations);
		}
		catch (RuntimeException e) {
		    e.printStackTrace();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
        logger.debug("\t\t "+filename+" annotated.");
    }

    @Override
    public String toString() {
        return this.filename;
    }
}
