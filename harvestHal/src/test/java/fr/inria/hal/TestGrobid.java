package fr.inria.hal;

import fr.inria.hal.Grobid;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import org.apache.commons.io.FileUtils;

/**
 *  @author Patrice Lopez
 */
public class TestGrobid extends BaseTest {
	
	//@Test
	public void testFullText() throws Exception {
		Grobid grobid = new Grobid();
		
		File pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586.pdf");
		if (!pdfFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		
		String fulltext = grobid.runFullTextGrobid(pdfFile.getPath(), 2, -1, true);
		FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586.fulltext.tei.xml"), fulltext, "UTF-8");
		// some test here...
		
		pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668.pdf");
		if (!pdfFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		
		fulltext = grobid.runFullTextGrobid(pdfFile.getPath(), 2, -1, true);
		FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668.fulltext.tei.xml"), fulltext, "UTF-8");
		// some test here...
	}
	
}