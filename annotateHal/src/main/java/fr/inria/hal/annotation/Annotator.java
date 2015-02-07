package fr.inria.hal.annotation;

import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.net.*;

import java.util.concurrent.*;

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
 *  Use the NERD REST service for annotating HAL TEI documents. Resulting JSON annotations are then stored
 *  in MongoDB as persistent storage. 
 *
 *  @author Patrice Lopez
 */
public class Annotator {
	private static final Logger logger = LoggerFactory.getLogger(Annotator.class);
	
	private String nerd_host = null;
	private String nerd_port = null;
		
	private int nbThreads = 1;	
		
	public Annotator() {
		loadProperties();
	}
	
	private void loadProperties() {
		try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("annotateHal.properties"));
			nerd_host = prop.getProperty("org.annotateHal.nerd_host");			
			nerd_port = prop.getProperty("org.annotateHal.nerd_port");
			String threads = prop.getProperty("org.annotateHal.nbThreads");
			try {
				nbThreads = Integer.parseInt(threads);
			}
			catch(java.lang.NumberFormatException e) {
				e.printStackTrace();
			}
		}
		catch (Exception e) {
			System.err.println("Failed to load properties: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public int annotateCollection() {
		int nb = 0;
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
        //docFactory.setNamespaceAware(true);
		try {
        	DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		
			// loop on the TEI documents in MongoDB
			MongoManager mm = new MongoManager();
			
			if (mm.initGridFS()) {
				int i = 0;
			
				while(mm.hasMoreDocuments()) {
					String halID = mm.getCurrentHalID();
					String filename = mm.getCurrentFilename();
					String tei = mm.nextDocument();

					List<String> halDomainTexts = new ArrayList<String>();
					List<String> halDomains = new ArrayList<String>();
					List<String> meSHDescriptors = new ArrayList<String>();
					
					try {
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
						String jsonAnnotations = 
							annotateDocument(docTei, filename, halID, nerd_host, nerd_port);						
						mm.insertAnnotation(jsonAnnotations);
						nb++;
					}
					catch(Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return nb;
	}
	
	public int annotateCollectionMultiThreaded() {
		// max queue of tasks of 50 
		BlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<Runnable>(50);
		ThreadPoolExecutor executor = new ThreadPoolExecutor(nbThreads, nbThreads, 50000, 
			TimeUnit.MILLISECONDS, blockingQueue);
		
		// this is for handling rejected tasks (e.g. queue is full)
		executor.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r,
                    ThreadPoolExecutor executor) {
                System.out.println("Task Rejected : "
                        + ((AnnotatorWorker) r).getFilename());
                System.out.println("Waiting for 60 second !!");
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Lets add another time : "
                        + ((AnnotatorWorker) r).getFilename());
                executor.execute(r);
            }
        });
		executor.prestartAllCoreThreads();
		int nb = 0;
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
        //docFactory.setNamespaceAware(true);
		try {
        	DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		
			// loop on the TEI documents in MongoDB
			MongoManager mm = new MongoManager();
			
			if (mm.initGridFS()) {
				int i = 0;
			
				while(mm.hasMoreDocuments()) {
					String filename = mm.getCurrentFilename();
					String halID = mm.getCurrentHalID();
					
					// check if the document is already annotated
					if (mm.isAnnotated()) {
						System.out.println("skipping " + filename + ": already annotated");
						mm.nextDocument();
						continue;
					}
					
					String tei = mm.nextDocument();
					// filter based on document size... we should actually annotate only 
					// a given length and then stop
					if (tei.length() > 300000) {
						System.out.println("skipping " + filename + ": file too large");
						continue;
					}
						
                    Runnable worker = 
						new AnnotatorWorker(mm, filename, halID, tei, nerd_host, nerd_port);
                    executor.execute(worker);
					nb++;
				}
			}
	        executor.shutdown();
	        while (!executor.isTerminated()) {
	        }
	        System.out.println("Finished all threads");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return nb;
	
	}
	
	public String annotateDocument(Document doc, 
								String filename, 
								String halID) {
		return annotateDocument(doc,filename, halID, nerd_host, nerd_port);						
	}
	
	/**
	 *  Annotation of a complete document
	 */
	public static String annotateDocument(Document doc, 
								String filename, 
								String halID, 
								String nerd_host, 
								String nerd_port) {
		StringBuffer json = new StringBuffer();
		json.append("{ \"filename\" : \"" + filename + 
					  "\", \"halID\" : \"" + halID + 
					  "\", \"nerd\" : [");
		annotateNode(doc.getDocumentElement(), true, json, nerd_host, nerd_port);							
		json.append("] }");
		return json.toString();	
	}
	
	/**
	 *  Recursive tree walk for annotating every nodes having a random xml:id
	 */
	public static boolean annotateNode(Node node, 
							boolean first, 
							StringBuffer json, 
							String nerd_host, 
							String nerd_port) {
		if (node.getNodeType() == Node.ELEMENT_NODE) {
			Element e = (Element)(node);
			String id = e.getAttribute("xml:id");
			if (id.startsWith("_") && (id.length() == 8)) {
				// get the textual content of the element
				// annotate
				String text = e.getTextContent();
				String jsonText = null;
				try {					
					NerdService nerdService = new NerdService(text, nerd_host, nerd_port);
					jsonText = nerdService.runNerd();
				}
				catch(Exception ex) {
					logger.debug("Text could not be annotated by NERD: " + text);
					ex.printStackTrace();
				}
				if (jsonText != null) {
					// resulting annotations, with the corresponding id
					if (first)
						first = false;
					else
						json.append(", ");
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
	
    public static void main(String[] args)
        throws IOException, ClassNotFoundException, 
               InstantiationException, IllegalAccessException {
		
        Annotator annotator = new Annotator();
		
		// loading based on DocDB XML, with TEI conversion
		try {
			int nbAnnots = annotator.annotateCollectionMultiThreaded();
			System.out.println("Total: " + nbAnnots + " annotations produced.");
		}
		catch(Exception e) {
			System.err.println("Error when setting-up the annotator.");
			e.printStackTrace();
		}
    }
}