package fr.inria.anhalytics.index;

import fr.inria.anhalytics.index.ElasticSearchManager;
import fr.inria.anhalytics.commons.managers.MongoManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.*;
import org.apache.commons.io.FileUtils;

import fr.inria.anhalytics.index.jsonML.JsonTapasML;
import fr.inria.anhalytics.index.jsonML.JSONArray;
import fr.inria.anhalytics.index.jsonML.JSONObject;

import fr.inria.anhalytics.commons.utilities.IndexingPreprocess;

/**
 * @author Patrice Lopez
 */
public class TestIndexing {

 /*   public File getResourceDir(String resourceDir) throws Exception {
        File file = new File(resourceDir);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw new Exception("Cannot start test, because test resource folder is not correctly set.");
            }
        }
        return (file);
    }

    @Test
    public void testIndexingFullText() throws Exception {
        ElasticSearchManager esm = new ElasticSearchManager();
        MongoManager mm = new MongoManager();
        IndexingPreprocess indexingPreprocess = new IndexingPreprocess(mm);
        esm.setUpElasticSearch();

        File teiFile = new File(this.getResourceDir("src/test/resources/").getPath() + "/hal-01110586v1.final.tei.xml");
        String tei = FileUtils.readFileToString(teiFile, "UTF-8");
        JSONObject json = JsonTapasML.toJSONObject(tei);
        String jsonStr = json.toString();
        try {
            jsonStr = indexingPreprocess.process(jsonStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        esm.index(jsonStr, "1");

        teiFile = new File(this.getResourceDir("src/test/resources/").getPath() + "/hal-01110668v1.final.tei.xml");
        tei = FileUtils.readFileToString(teiFile, "UTF-8");
        json = JsonTapasML.toJSONObject(tei);
        jsonStr = json.toString();
        try {
            jsonStr = indexingPreprocess.process(jsonStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        esm.index(jsonStr, "2");
        mm.close();
    }
*/
    /**
     * Load a set of TEI documents to be indexed
     */
   /* private void loadTestCollection(String pathToTEIRepository) throws Exception {
        MongoManager mm = null;
        if (pathToTEIRepository == null) {
            throw new Exception("Path for TEI data invalid: " + pathToTEIRepository);
        }
        try {
            File corpusDir = new File(pathToTEIRepository);
            if (!corpusDir.exists()) {
                throw new Exception("Repository for TEI data does not open: " + pathToTEIRepository);
            }

            final File[] refFiles = corpusDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".tei.xml");
                }
            });

            if (refFiles == null) {
                throw new IllegalStateException("Folder " + corpusDir.getAbsolutePath()
                        + " does not seem to contain training data. Please check");
            }

            mm = new MongoManager();
            if (!mm.init()) {
                throw new IllegalStateException("Cannot start MongoDB client.");
            }

            int n = 0;
            for (; n < refFiles.length; n++) {
                final File teifile = refFiles[n];
                String name = teifile.getName();

                mm.addDocument(teifile.getAbsolutePath(), name, new Date());
            }
        } finally {
            if (mm != null) {
                mm.close();
            }
        }
    }

    @Test
    public void testIndexingCollection() throws Exception {
        ElasticSearchManager esm = new ElasticSearchManager();
        try {
			//loadTestCollection("../tempTEI");

            esm.setUpElasticSearch();
            int nbDoc = esm.indexCollection();

            System.out.println("Total: " + nbDoc + " documents indexed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
*/
}
