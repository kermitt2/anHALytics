package fr.inria.hal.annotation;

import java.io.*;
import java.util.*;
import java.net.*;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
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
import org.elasticsearch.action.get.*;

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

import org.codehaus.jackson.*;
import org.codehaus.jackson.node.*;
import org.codehaus.jackson.map.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Methods for indexing the annotations in the ElasticSearch cluster.  
 *
 *  @author Patrice Lopez
 */
public class ElasticSearchManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchManager.class);
	
	private String elasticSearch_host = null;
	private String elasticSearch_port = null;
	private String elasticSearchClusterName = null;
	private String indexName = null;
	private Client client = null;
	
	// only annotations under these paths will be indexed for the moment
	static final public List<String> toBeIndexed = 
		Arrays.asList("$TEI.$teiHeader.$titleStmt.xml:id", 
			"$TEI.$teiHeader.$profileDesc.xml:id", 
			"$TEI.$teiHeader.$profileDesc.$textClass.$keywords.$type_author.xml:id");
	
	private void loadProperties() {
		try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("annotateHal.properties"));
			elasticSearch_host = prop.getProperty("org.annotateHal.elasticSearch_host");			
			elasticSearch_port = prop.getProperty("org.annotateHal.elasticSearch_port");
			elasticSearchClusterName = prop.getProperty("org.annotateHal.elasticSearch_cluster");
			indexName = prop.getProperty("org.annotateHal.elasticSearch_indexName");
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
		urlStr += "/annotation/_mapping";
		
		URL url = new URL(urlStr);
		HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
		httpCon.setDoOutput(true);
		httpCon.setRequestProperty(
		    "Content-Type", "application/x-www-form-urlencoded" );
		httpCon.setRequestMethod("PUT");
		String mappingStr = null;
		try {
			File file = new File("src/main/resources/elasticSearch/annotation.json");
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
	 *  Launch the indexing of the HAL annotations in ElasticSearch
	 */	
	public int index() throws Exception {
		Settings settings = ImmutableSettings.settingsBuilder()
		        .put("cluster.name", elasticSearchClusterName).build();
		Client client = new TransportClient(settings)
		        .addTransportAddress(new InetSocketTransportAddress(elasticSearch_host, 9300));
		MongoManager mm = null;
		int nb = 0;
		try {
			mm = new MongoManager();
			ObjectMapper mapper = new ObjectMapper();
		
			if (mm.initAnnotations()) {
				int i = 0;
				BulkRequestBuilder bulkRequest = client.prepareBulk();
				bulkRequest.setRefresh(true);
				while(mm.hasMoreAnnotations()) {
					String json = mm.nextAnnotation();
					String filename = mm.getCurrentAnnotationFilename();
					String halID = mm.getCurrentAnnotationHalID();
					
					// get the xml:id of the elements we want to index from the document
					// we only index title, abstract and keyphrase annotations !
					List<String> validIDs = validDocIDs(halID, mapper);
					//System.out.println(validIDs.toString());
					JsonNode jsonAnnotation= mapper.readTree(json);
					JsonNode newNode = mapper.createObjectNode(); 
					
					Iterator<JsonNode> ite = jsonAnnotation.getElements();
					while (ite.hasNext()) {
						JsonNode temp = ite.next();
						JsonNode idNode = temp.findValue("xml:id");
						String xmlID = idNode.getTextValue();
						//System.out.println(xmlID);						
						if (!validIDs.contains(xmlID)) {
							continue;
						}
						
						((ObjectNode)newNode).put("annotation", temp);
						String annotJson = newNode.toString();
						//System.out.println(annotJson);
				
						// we do not index the empty annotation results! 
						// the nerd subdoc has no entites field
						JsonNode nerdNode = temp.findPath("nerd");
						JsonNode entitiesNode = null;
						if ( (nerdNode != null) && (!nerdNode.isMissingNode()) )
							entitiesNode = nerdNode.findPath("entities");
				
						if ( (entitiesNode == null) || entitiesNode.isMissingNode() ) {
							//System.out.println("Skipping " + annotJson);
							continue;
						}
						
						// index the json in ElasticSearch
						try {
							// beware the document type bellow and corresponding mapping!
							bulkRequest.add(client.prepareIndex(indexName, "annotation", xmlID).setSource(annotJson));
					
							if (i >= 500) {
								BulkResponse bulkResponse = bulkRequest.execute().actionGet();
								if (bulkResponse.hasFailures()) {
							    	// process failures by iterating through each bulk response item	
									System.out.println(bulkResponse.buildFailureMessage()); 
								}
								bulkRequest = client.prepareBulk();
								bulkRequest.setRefresh(true);
								i = 0;
								System.out.print(".");
								System.out.flush();
							}
				
							i++;
							nb++;
						}
						catch(Exception e) {
							e.printStackTrace();
						}
					}
				}
				// last bulk
				BulkResponse bulkResponse = bulkRequest.execute().actionGet();
				if (bulkResponse.hasFailures()) {
			    	// process failures by iterating through each bulk response item	
					System.out.println(bulkResponse.buildFailureMessage());
				}
				System.out.print("\n");
			}
		}
		finally {	
			if (mm != null)
				mm.close();
		}
		return nb;
	}
	

	private List<String> validDocIDs(String halID, ObjectMapper mapper) {
		List<String> results = new ArrayList<String>();
		System.out.println("validDocIDs: " + halID);
		
		String request = "{\"fields\": [ ";
		boolean first = true;
		for(String path : toBeIndexed) {
			if (first) {
				first = false;
			}
			else 
				request += ", ";
			request += "\""+path+"\"";
		}
		request += "], \"query\": { \"filtered\": { \"query\": { \"term\": {\"_id\": \"" + halID +"\"}}}}}";
		//System.out.println(request);
		
		String urlStr = "http://"+elasticSearch_host+":"+elasticSearch_port+"/hal/_search";
		StringBuffer json = new StringBuffer();
		try {
			URL url = new URL(urlStr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json; charset=utf8");
		
			byte[] postDataBytes = request.getBytes("UTF-8");

			OutputStream os = conn.getOutputStream();
			os.write(postDataBytes);
			os.flush();
			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				System.out.println("Failed, HTTP error code : "
					+ conn.getResponseCode());
				return null;
			}
			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
			String line = null;
			while ((line = br.readLine()) != null) {
				json.append(line);
				json.append(" ");
			}
			os.close();
			conn.disconnect();
		}
		catch (MalformedURLException e) {
			e.printStackTrace();
	  	} 
		catch (IOException e) {
			e.printStackTrace();
		}
		
		//System.out.println(json.toString());
		try { 
			JsonNode resJsonStruct = mapper.readTree(json.toString());
			JsonNode hits = resJsonStruct.findPath("hits").findPath("hits");
			if (hits.isArray()) {
				JsonNode hit0 = hits.get(0);
				if (hit0 != null) {
					JsonNode fields = hit0.findPath("fields");
					Iterator<JsonNode> ite = fields.getElements();
					while(ite.hasNext()) {
						JsonNode idNodes = (JsonNode)ite.next();
						if (idNodes.isArray()) {
							Iterator<JsonNode> ite2 = idNodes.getElements();
							while(ite2.hasNext()) {
								JsonNode node = (JsonNode)ite2.next();
						
								results.add(node.getTextValue());
							}
						}
					}
				}
			}
			
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return results;
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
			int nbAnnots = esm.index();
			
			System.out.println("Total: " + nbAnnots + " annotations indexed.");
		}
		catch(Exception e) {
			System.err.println("Error when setting-up ElasticSeach cluster");
			e.printStackTrace();
		}
    }

}