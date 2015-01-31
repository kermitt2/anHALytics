package org.harvesthal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import org.apache.commons.io.FileUtils;

/**
 *  @author Patrice Lopez
 */
public class TestGrobidService extends BaseTest {
	
	//@Test
	public void testFullText() throws Exception {
		GrobidService grobid = new GrobidService();
		
		File pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586.pdf");
		if (!pdfFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		
		String fulltext = grobid.runFullTextGrobid(pdfFile.getPath());
		// some test here...
		System.out.println(fulltext);
		
		pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668.pdf");
		if (!pdfFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		
		fulltext = grobid.runFullTextGrobid(pdfFile.getPath());
		// some test here...
		System.out.println(fulltext);
	}
	
}