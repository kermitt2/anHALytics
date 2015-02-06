package org.annotateHal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import org.apache.commons.io.FileUtils;

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
	
/**
 *  @author Patrice Lopez
 */
public class TestAnnotator {

	public File getResourceDir(String resourceDir) throws Exception {
		File file = new File(resourceDir);
		if (!file.exists()) {
			if (!file.mkdirs()) {
				throw new Exception("Cannot start test, because test resource folder is not correctly set.");
			}
		}
		return(file);
	}
	
	@Test
	public void testAnnotateFullText() throws Exception {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
		Annotator annotator = new Annotator();
		DocumentBuilder docBuilder = null;
		MongoManager mm = null;

		try {
			File teiFile = new File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110586v1.final.tei.xml");
			String tei = FileUtils.readFileToString(teiFile, "UTF-8");
			
        	docBuilder = docFactory.newDocumentBuilder();
			mm = new MongoManager();

			Document docTei = docBuilder.parse(new InputSource(new ByteArrayInputStream(tei.getBytes("utf-8"))));
			String json = annotator.annotateDocument(docTei, "1", "1");
			System.out.println(json);
			mm.insertAnnotation(json);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		/*try {
			File teiFile = new File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110668v1.final.tei.xml");
			String tei = FileUtils.readFileToString(teiFile, "UTF-8");		
			
			Document docTei = docBuilder.parse(new InputSource(new ByteArrayInputStream(tei.getBytes("utf-8"))));
			annotator.annotateNode(docTei.getDocumentElement(), docTei, mm, "2", "2");
		}
		catch(Exception e) {
			e.printStackTrace();
		}*/
	}
		
}