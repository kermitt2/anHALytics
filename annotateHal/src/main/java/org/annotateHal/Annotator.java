package org.annotateHal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Use NERD REST service for annotating HAL TEI documents. Resulting JSON annotations are then stored
 *  in MongoDB as persistent storage. 
 *
 *  @author Patrice Lopez
 */
public class Annotator {
	
	private String nerd_host = null;
	private String nerd_port = null;
	
	private void loadProperties() {
		try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("annotateHal.properties"));
			nerd_host = prop.getProperty("org.annotateHal.nerd_host");			
			nerd_port = prop.getProperty("org.annotateHal.nerd_port");
		}
		catch (Exception e) {
			System.err.println("Failed to load properties: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	
	
}