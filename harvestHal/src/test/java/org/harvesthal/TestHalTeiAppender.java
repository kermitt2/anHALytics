package org.harvesthal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;

import org.apache.commons.io.FileUtils;

/**
 *  @author Patrice Lopez
 */
public class TestHalTeiAppender extends BaseTest {

	@Test
	public void testTEIMerging() throws Exception {
		File halTeiFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586v1.tei.xml");
		if (!halTeiFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		
		File fullTextFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.fulltext.tei.xml");
		if (!fullTextFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		
		String result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), new FileInputStream(fullTextFile), false);
		//System.out.println(result);
		// some test here...
		/*FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586.final.tei.xml"), result, "UTF-8");*/
		
		halTeiFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
					"/hal-01110668v1.tei.xml");
		if (!halTeiFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}

		fullTextFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.fulltext.tei.xml");
		if (!fullTextFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}

		result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), new FileInputStream(fullTextFile), false);
		//System.out.println(result);
		/*FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.final.tei.xml"), result, "UTF-8");*/
		// some test here...
	}
	
	@Test
	public void testTEIMergingBrutal() throws Exception {
		File halTeiFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586v1.tei.xml");
		if (!halTeiFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		
		File fullTextFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.fulltext.tei.xml");
		if (!fullTextFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		
		String result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), new FileInputStream(fullTextFile), true);
		//System.out.println(result);
		// some test here...
		
		halTeiFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
					"/hal-01110668v1.tei.xml");
		if (!halTeiFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}

		fullTextFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.fulltext.tei.xml");
		if (!fullTextFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}

		result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), new FileInputStream(fullTextFile), true);
		//System.out.println(result);
		// some test here...
	}


}