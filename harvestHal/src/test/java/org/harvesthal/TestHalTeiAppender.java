package org.harvesthal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;

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
		
		String result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), fullTextFile.getPath());
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

		result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), fullTextFile.getPath());
		//System.out.println(result);
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
		
		String result = HalTeiAppender.replaceHeaderBrutal(new FileInputStream(halTeiFile), fullTextFile.getPath());
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

		result = HalTeiAppender.replaceHeaderBrutal(new FileInputStream(halTeiFile), fullTextFile.getPath());
		//System.out.println(result);
		
	}


}