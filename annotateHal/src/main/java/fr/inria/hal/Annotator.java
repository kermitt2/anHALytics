package fr.inria.hal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 *  Use the NERD REST service for annotating HAL TEI documents. Resulting JSON annotations are then stored
 *  in MongoDB as persistent storage. 
 *
 *  @author Patrice Lopez
 */
public class Annotator {
	
	private String nerd_host = null;
	private String nerd_port = null;
	
	static private String RESOURCEPATH = "";
	
	public Annotator() {
		loadProperties();
	}
	
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
	
	public void annotateNERD() throws Exception {
		try {
			URL url = new URL("http://" + nerd_host + ":" + nerd_port + "/" + RESOURCEPATH);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");

			// text to be annotated
			String input = "";

			OutputStream os = conn.getOutputStream();
			os.write(input.getBytes());
			os.flush();
			if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
				throw new RuntimeException("Failed : HTTP error code : "
					+ conn.getResponseCode());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

			String output;
			System.out.println("Output from Server .... \n");
			while ((output = br.readLine()) != null) {
				System.out.println(output);
			}

			conn.disconnect();
		}
		catch (MalformedURLException e) {
			e.printStackTrace();
	  	} 
		catch (IOException e) {
			e.printStackTrace();
		}
 
	}
	
	
	public int annotateCollection() {
		// loop on the TEI documents in MongoDB
		MongoManager mm = new MongoManager();
		int nb = 0;
		try {
			if (mm.initGridFS()) {
				int i = 0;
			
				while(mm.hasMoreDocuments()) {
					String halID = mm.getCurrentHalID();
					String tei = mm.nextDocument();
				
				
				
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return nb;
	}
	
    public static void main(String[] args)
        throws IOException, ClassNotFoundException, 
               InstantiationException, IllegalAccessException {
		
        Annotator annotator = new Annotator();
		
		// loading based on DocDB XML, with TEI conversion
		try {
			int nbAnnots = annotator.annotateCollection();
			System.out.println("Total: " + nbAnnots + " annotations produced.");
		}
		catch(Exception e) {
			System.err.println("Error when setting-up the annotator.");
			e.printStackTrace();
		}
    }
}