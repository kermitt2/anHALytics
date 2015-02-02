package org.harvesthal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.io.*;
import java.util.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;

/**
 *  Call of Grobid process via its REST web services. 
 *
 *  @author Patrice Lopez
 */
public class GrobidService {

    private String grobid_host = null;
    private String grobid_port = null;
    private String pdf_path = null;
	private int start = -1;
	private int end = -1;
	private boolean generateIDs = false;

    public GrobidService(String pdfPath, String grobidHost, String grobidPort, 
						int start, int end, boolean generateIDs) {
		pdf_path = pdfPath;
        grobid_host = grobidHost;
        grobid_port = grobidPort;
		this.start = start;
		this.end = end;
		this.generateIDs = generateIDs;
    }
	
	/**
	 *  Call the Grobid full text extraction service on server.
	 *
	 *  @param pdfPath path to the PDF file to be processed
	 *  @param start first page of the PDF to be processed, default -1 first page
	 *  @param last last page of the PDF to be processed, default -1 last page	
	 *  @return the resulting TEI document as a String or null if the service failed	
	 */
    public String runFullTextGrobid() {
		String tei = null;
		try {
			URL url = new URL("http://" + grobid_host + ":" + grobid_port + "/processFulltextDocument");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/xml");

			FileBody fileBody = new FileBody(new File(pdf_path));
			MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.STRICT);
			multipartEntity.addPart("input", fileBody);
			
			if (start != -1) {	
				StringBody contentString = new StringBody(""+start);
				multipartEntity.addPart("start", contentString);
			}
			if (end != -1) {
				StringBody contentString = new StringBody(""+end);
				multipartEntity.addPart("end", contentString);
			}
			if (generateIDs) {
				StringBody contentString = new StringBody("1");
				multipartEntity.addPart("generateIDs", contentString);
			}
			
			conn.setRequestProperty("Content-Type", multipartEntity.getContentType().getValue());
			OutputStream out = conn.getOutputStream();
			try {
			    multipartEntity.writeTo(out);
			} finally {
			    out.close();
			}
                        
                        if (conn.getResponseCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                            throw new HttpRetryException("Failed : HTTP error code : "
                                    + conn.getResponseCode(), conn.getResponseCode());
                        }
                        
			//int status = connection.getResponseCode();
			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new RuntimeException("Failed : HTTP error code : "
					+ conn.getResponseCode());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
			StringBuffer output = new StringBuffer();
			String line = null;
			while ((line = br.readLine()) != null) {
				output.append(line);
				output.append("\n");
			}
                        
			tei = output.toString();
                        tei = tei.replace("&amp\\s+;", "&amp;");
			conn.disconnect();
		}
                catch (HttpRetryException e) {
                    e.printStackTrace();
                    try {
                        Thread.sleep(20000);
                        runFullTextGrobid();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
		catch (MalformedURLException e) {
			e.printStackTrace();
	  	} 
		catch (IOException e) {
			e.printStackTrace();
		}
		return tei;
    }
	
}