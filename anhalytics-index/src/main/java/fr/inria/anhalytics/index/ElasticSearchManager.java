package fr.inria.anhalytics.index;

import fr.inria.anhalytics.commons.managers.MongoManager;
import java.io.*;
import java.util.*;

import java.net.*;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.common.settings.*;
import org.elasticsearch.client.*;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.action.index.*;
import org.elasticsearch.common.transport.*;
import org.elasticsearch.client.transport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fr.inria.anhalytics.index.jsonML.JsonTapasML;
import fr.inria.anhalytics.index.jsonML.JSONObject;
import fr.inria.anhalytics.commons.utilities.IndexingPreprocess;

/**
 * Method for management of the ElasticSearch cluster.
 *
 * @author Patrice Lopez
 */
public class ElasticSearchManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchManager.class);

    private String elasticSearch_host = null;
    private String elasticSearch_port = null;
    private String elasticSearchClusterName = null;
    private String indexName = null;
    private Client client = null;

    private void loadProperties() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("index.properties"));
            elasticSearch_host = prop.getProperty("index.elasticSearch_host");
            elasticSearch_port = prop.getProperty("index.elasticSearch_port");
            elasticSearchClusterName = prop.getProperty("index.elasticSearch_cluster");
            indexName = prop.getProperty("index.elasticSearch_indexName");
        } catch (Exception e) {
            System.err.println("Failed to load properties: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * set-up ElasticSearch by loading the mapping and river json for the HAL
     * document database
     */
    public void setUpElasticSearch() throws Exception {
        try {
            loadProperties();

            // delete previous index
            deleteIndex();

            // create new index and load the appropriate mapping
            createIndex();
            loadMapping();
        } catch (Exception e) {
            throw new Exception("Sep-up of ElasticSearch failed for HAL index.", e);
        }
    }

    /**
     *
     */
    private boolean deleteIndex() throws Exception {
        boolean val = false;
        try {
            String urlStr = "http://" + elasticSearch_host + ":" + elasticSearch_port + "/" + indexName;
            URL url = new URL(urlStr);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestProperty(
                    "Content-Type", "application/x-www-form-urlencoded");
            httpCon.setRequestMethod("DELETE");
            httpCon.connect();
            System.out.println("ElasticSearch Index " + indexName + " deleted: status is "
                    + httpCon.getResponseCode());
            if (httpCon.getResponseCode() == 200) {
                val = true;
            }
            httpCon.disconnect();
        } catch (Exception e) {
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
        String urlStr = "http://" + elasticSearch_host + ":" + elasticSearch_port + "/" + indexName;
        URL url = new URL(urlStr);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestProperty(
                "Content-Type", "application/x-www-form-urlencoded");
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
        } catch (Exception e) {
            throw new Exception("Cannot read analyzer for " + indexName);
        }

        httpCon.setDoOutput(true);
        httpCon.setRequestMethod("PUT");
        httpCon.addRequestProperty("Content-Type", "text/json");
        OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
        out.write(analyserStr);
        out.close();

        System.out.println("ElasticSearch analyzer for " + indexName + " : status is "
                + httpCon.getResponseCode());
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

        String urlStr = "http://" + elasticSearch_host + ":" + elasticSearch_port + "/" + indexName;
        urlStr += "/npl/_mapping";

        URL url = new URL(urlStr);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestProperty(
                "Content-Type", "application/x-www-form-urlencoded");
        httpCon.setRequestMethod("PUT");
        String mappingStr = null;
        try {
            File file = new File("src/main/resources/elasticSearch/npl.json");
            mappingStr = FileUtils.readFileToString(file, "UTF-8");
        } catch (Exception e) {
            throw new Exception("Cannot read mapping for " + indexName);
        }

        System.out.println(urlStr);

        httpCon.setDoOutput(true);
        httpCon.setRequestMethod("PUT");
        httpCon.addRequestProperty("Content-Type", "text/json");
        OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
        out.write(mappingStr);
        out.close();

        System.out.println("ElasticSearch mapping for " + indexName + " : status is "
                + httpCon.getResponseCode());
        if (httpCon.getResponseCode() == 200) {
            val = true;
        }
        return val;
    }

    /**
     * Launch the indexing of the HAL collection in ElasticSearch
     */
    public int indexCollection() throws Exception {
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("cluster.name", elasticSearchClusterName).build();
        Client client = new TransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(elasticSearch_host, 9300));

        MongoManager mm = new MongoManager();
        IndexingPreprocess indexingPreprocess = new IndexingPreprocess(mm);
        int nb = 0;

        if (mm.init(MongoManager.HALHEADER_GROBIDBODY_TEIS, null)) {
            int i = 0;
            BulkRequestBuilder bulkRequest = client.prepareBulk();
            bulkRequest.setRefresh(true);
            while (mm.hasMoreDocuments()) {
                String filename = mm.getCurrentFilename();
                String halID = mm.getCurrentHalID();
                String tei = mm.nextDocument();

				// convert the TEI document into JSON via JsonML
                //System.out.println(halID);
                JSONObject json = JsonTapasML.toJSONObject(tei);
                String jsonStr = json.toString();
                try {
                    jsonStr = indexingPreprocess.process(jsonStr, filename);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //System.out.println(jsonStr);

                if (jsonStr == null) {
                    continue;
                }

                // index the json in ElasticSearch
                try {
                    // beware the document type bellow and corresponding mapping!
                    bulkRequest.add(client.prepareIndex(indexName, "npl", halID).setSource(jsonStr));

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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // last bulk
            BulkResponse bulkResponse = bulkRequest.execute().actionGet();
            if (bulkResponse.hasFailures()) {
                // process failures by iterating through each bulk response item	
                System.out.println(bulkResponse.buildFailureMessage());
            }
        }
        client.close();

        return nb;
    }

    public void index(String json, String halID) {
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("cluster.name", elasticSearchClusterName).build();
        Client client = new TransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(elasticSearch_host, 9300));

        IndexResponse response = client.prepareIndex(indexName, "npl", halID)
                .setSource(json)
                .execute()
                .actionGet();
        client.close();
    }

    /**
     * Set-up ElasticSearch.
     */
    public static void main(String[] args)
            throws IOException, ClassNotFoundException,
            InstantiationException, IllegalAccessException {

        ElasticSearchManager esm = new ElasticSearchManager();

        // loading based on DocDB XML, with TEI conversion
        try {
            esm.setUpElasticSearch();
            int nbDoc = esm.indexCollection();

            System.out.println("Total: " + nbDoc + " documents indexed.");
        } catch (Exception e) {
            System.err.println("Error when setting-up ElasticSeach cluster");
            e.printStackTrace();
        }
    }

}
