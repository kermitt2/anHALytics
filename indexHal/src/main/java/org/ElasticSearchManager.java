package org.indexHal;

import java.io.*;
import java.util.*;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import java.net.*;
import org.apache.commons.io.FileUtils;

import static org.elasticsearch.common.xcontent.XContentFactory.*;
import static org.elasticsearch.node.NodeBuilder.*;
import org.elasticsearch.common.settings.*;
import org.elasticsearch.client.*;
import org.elasticsearch.node.*;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.action.index.*;
import org.elasticsearch.common.transport.*;
import org.elasticsearch.client.transport.*;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.FilterBuilders.*;
import org.elasticsearch.index.query.QueryBuilders.*;
import org.elasticsearch.search.*;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.sort.*;

import org.codehaus.jackson.*;
import org.codehaus.jackson.node.*;
import org.codehaus.jackson.map.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Method for management of the ElasticSearch cluster.  
 *
 *  @author Patrice Lopez
 */
 
public class ElasticSearchManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchManager.class);
	
	private String elasticSearch_host = null;
	private String elasticSearch_port;
	private String indexName = null;
	
	private void loadProperties() {
		try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("indexHal.properties"));
			elasticSearch_host = prop.getProperty("org.indexHal.elasticSearch_host");			
			elasticSearch_port = prop.getProperty("org.indexHal.elasticSearch_port");
			indexName = prop.getProperty("org.indexHal.elasticSearch_indexName");
		}
		catch (Exception e) {
			System.err.println("Failed to load properties: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * set-up ElasticSearch by loading the mapping and river json for the HAL document database
	 */
	public void setUpElasticSearch() throws Exception {
		try {
			loadProperties();
		
			// delete previous index
			deleteIndex();
			
			// create new index and load the appropriate mapping
			createIndex();
			loadMapping();
		}
		catch(Exception e) {
			throw new Exception("Sep-up of ElasticSearch failed for HAL index.", e);
		}
	}
	
	/**
	 * 
	 */
	private boolean deleteIndex() throws Exception {
		boolean val = false;
		try {
			String urlStr = "http://"+elasticSearch_host+":"+elasticSearch_port+"/"+indexName;
			URL url = new URL(urlStr);
			HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
			httpCon.setDoOutput(true);
			httpCon.setRequestProperty(
			    "Content-Type", "application/x-www-form-urlencoded" );
			httpCon.setRequestMethod("DELETE");
			httpCon.connect();
			System.out.println("ElasticSearch Index " + indexName + " deleted: status is " + 
				httpCon.getResponseCode());
			if (httpCon.getResponseCode() == 200) {
				val = true;
			}
			httpCon.disconnect();
		}
		catch(Exception e) {
			throw new Exception("Cannot delete index for " + indexName);
		}
		return val;
	}

	/**
	 *
	 */
	private boolean createIndex() throws Exception {
		boolean val = false;
		
		// create index
		String urlStr = "http://"+elasticSearch_host+":"+elasticSearch_port+"/"+indexName;
		URL url = new URL(urlStr);
		HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
		httpCon.setDoOutput(true);
		httpCon.setRequestProperty(
		    "Content-Type", "application/x-www-form-urlencoded" );
		httpCon.setRequestMethod("PUT");
		
		/*System.out.println("ElasticSearch Index " + indexName + " creation: status is " + 
			httpCon.getResponseCode());
		if (httpCon.getResponseCode() == 200) {
			val = true;
		}*/
		
		// load custom analyzer
		String analyserStr = null;
		try {
			File file = new File("src/main/resources/elasticSearch/analyzer.json");
			analyserStr = FileUtils.readFileToString(file, "UTF-8");
		}
		catch(Exception e) {
			throw new Exception("Cannot read analyzer for " + indexName);
		}
		
		httpCon.setDoOutput(true);
		httpCon.setRequestMethod("PUT");
		httpCon.addRequestProperty("Content-Type", "text/json");
		OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
		out.write(analyserStr);
		out.close();
		
		System.out.println("ElasticSearch analyzer for " + indexName + " : status is " + 
			httpCon.getResponseCode());
		if (httpCon.getResponseCode() == 200) {
			val = true;
		}
		
		httpCon.disconnect();
		return val;
	}
	
	/**
	 *
	 */
	private boolean loadMapping() throws Exception {
		boolean val = false;
		
		String urlStr = "http://"+elasticSearch_host+":"+elasticSearch_port+"/"+indexName;
		urlStr += "/npl_type/_mapping";
		
		URL url = new URL(urlStr);
		HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
		httpCon.setDoOutput(true);
		httpCon.setRequestProperty(
		    "Content-Type", "application/x-www-form-urlencoded" );
		httpCon.setRequestMethod("PUT");
		String mappingStr = null;
		try {
			File file = new File("src/main/resources/elasticSearch/mapping_npl.json");
			mappingStr = FileUtils.readFileToString(file, "UTF-8");
		}
		catch(Exception e) {
			throw new Exception("Cannot read mapping for " + indexName);
		}
		
		System.out.println(urlStr);
		
		httpCon.setDoOutput(true);
		httpCon.setRequestMethod("PUT");
		httpCon.addRequestProperty("Content-Type", "text/json");
		OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
		out.write(mappingStr);
		out.close();
		
		System.out.println("ElasticSearch mapping for " + indexName + " : status is " + 
			httpCon.getResponseCode());
		if (httpCon.getResponseCode() == 200) {
			val = true;
		}
		return val;
	}

	/**
	 *  Launch the indexing of the HAL collection in ElasticSearch
	 */	
	public int index() throws Exception {
		MongoManager mm = new MongoManager();
		int nb = 0;
		
		if (mm.init()) {
			while(mm.hasMoreDocuments()) {
				String tei = mm.next();
			
				// convert the TEI document into JSON via JsonML
	System.out.println(tei);			

				// index in ElasticSearch
			
				nb++;
			}
		}
				
		return nb;
	}
	

	/**
     *	Set-up ElasticSearch.
     */
    public static void main(String[] args)
        throws IOException, ClassNotFoundException, 
               InstantiationException, IllegalAccessException {
		
        ElasticSearchManager esm = new ElasticSearchManager();
		
		// loading based on DocDB XML, with TEI conversion
		try {
			esm.setUpElasticSearch();
			int nbDoc = esm.index();
			
			System.out.println("Total: " + nbDoc + " documents indexed.");
		}
		catch(Exception e) {
			System.err.println("Error when setting-up ElasticSeach cluster");
			e.printStackTrace();
		}
    }

}