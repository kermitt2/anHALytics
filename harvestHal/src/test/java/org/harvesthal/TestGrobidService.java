package org.harvesthal;

import java.io.ByteArrayInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.io.FileUtils;

/**
 *  @author Patrice Lopez
 */
public class TestGrobidService extends BaseTest {
	
	//@Test
	public void testFullText() throws Exception {
            Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("harvestHal.properties"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        ExecutorService executor = Executors.newFixedThreadPool(2);
            String grobid_host = prop.getProperty("harvestHal.grobid_host");
	    String grobid_port = prop.getProperty("harvestHal.grobid_port");
		File pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586.pdf");
		if (!pdfFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		Future<String> submit = executor.submit(
			new GrobidService(pdfFile.getPath(), grobid_host, grobid_port, 2, -1, true));                  
		String fulltext = submit.get();
		// some test here...
		//System.out.println(fulltext);
		FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586v1.fulltext.tei.xml"), fulltext, "UTF-8");
		
		pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668.pdf");
		if (!pdfFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		submit = executor.submit(new GrobidService(pdfFile.getPath(), grobid_host, grobid_port, 2, -1, true)); 
		fulltext = submit.get();
		// some test here...
		//System.out.println(fulltext);
		FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.fulltext.tei.xml"), fulltext, "UTF-8");
		
		/*pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/main.pdf");
		if (!pdfFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		
		fulltext = grobid.runFullTextGrobid(pdfFile.getPath(), 2, -1, true);
		// some test here...
		//System.out.println(fulltext);
		FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/main.fulltext.tei.xml"), fulltext, "UTF-8");*/
	}
	
}