package fr.inria.hal.annotate;

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
	
import com.mongodb.MongoClient;
import com.mongodb.DB;
import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.WriteConcern;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.ServerAddress;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;
import com.mongodb.CommandResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;	
	
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
	
	//@Test
	public void testAnnotateFullText() throws Exception {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
		Annotator annotator = new Annotator();
		DocumentBuilder docBuilder = null;
		MongoManager mm = null;

		try {
			File teiFile = new 
				File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110586v1.final.tei.xml");
			String tei = FileUtils.readFileToString(teiFile, "UTF-8");
			
        	docBuilder = docFactory.newDocumentBuilder();
			mm = new MongoManager();

			Document docTei = docBuilder.parse(new InputSource(new ByteArrayInputStream(tei.getBytes("utf-8"))));
			String json = annotator.annotateDocument(docTei, "1", "1");
			mm.insertAnnotation(json);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		try {
			File teiFile = new 
				File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110668v1.final.tei.xml");
			String tei = FileUtils.readFileToString(teiFile, "UTF-8");		
			
			Document docTei = docBuilder.parse(new InputSource(new ByteArrayInputStream(tei.getBytes("utf-8"))));
			String json = annotator.annotateDocument(docTei, "2", "2");
			
			mm.insertAnnotation(json);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}


	//@Test
	public void testAnnotateCollection() throws Exception {
		// insert two documents
		MongoManager mm = null;
		Annotator annotator = new Annotator();
		try {
			mm = new MongoManager();
			
			File teiFile = new 
				File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110586v1.final.tei.xml");
			String tei = FileUtils.readFileToString(teiFile, "UTF-8");
			mm = new MongoManager();
			mm.insertDocument("hal-01110586v1.final.tei.xml", tei);
			
			teiFile = new 
				File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110668v1.final.tei.xml");
			tei = FileUtils.readFileToString(teiFile, "UTF-8");
			mm.insertDocument("hal-01110668v1.final.tei.xml", tei);
			
			int nb = annotator.annotateCollection();
			System.out.println(nb + " documents annotated");
			
			// remove the documents
			mm.removeDocument("hal-01110668v1.final.tei.xml");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testAnnotateCollectionMultiThreaded() throws Exception {
		// insert two documents
		MongoManager mm = null;
		Annotator annotator = new Annotator();
		try {
			mm = new MongoManager();
			
			File teiFile = new 
				File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110586v1.final.tei.xml");
			String tei = FileUtils.readFileToString(teiFile, "UTF-8");
			mm = new MongoManager();
			mm.insertDocument("hal-01110586v1.final.tei.xml", tei);
			
			teiFile = new 
				File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110668v1.final.tei.xml");
			tei = FileUtils.readFileToString(teiFile, "UTF-8");
			mm.insertDocument("hal-01110668v1.final.tei.xml", tei);
			
			int nb = annotator.annotateCollectionMultiThreaded();
			System.out.println(nb + " documents annotated");
			
			// remove the documents
			mm.removeDocument("hal-01110668v1.final.tei.xml");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}