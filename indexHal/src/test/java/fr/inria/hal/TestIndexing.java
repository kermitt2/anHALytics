package fr.inria.hal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import org.apache.commons.io.FileUtils;

import fr.inria.hal.jsonML.JsonTapasML;
import fr.inria.hal.jsonML.JSONArray;
import fr.inria.hal.jsonML.JSONObject;

import fr.inria.hal.utilities.IndexingPreprocess;
import fr.inria.ha.ElasticSearchManager;

/**
 *  @author Patrice Lopez
 */
public class TestIndexing {

	public File getResourceDir(String resourceDir) throws Exception {
		File file = new File(resourceDir);
		if (!file.exists()) {
			if (!file.mkdirs()) {
				throw new Exception("Cannot start test, because test resource folder is not correctly set.");
			}
		}
		return(file);
	}
	
	@Test
	public void testIndexingFullText() throws Exception {
		ElasticSearchManager esm = new ElasticSearchManager();
		IndexingPreprocess indexingPreprocess = new IndexingPreprocess();
		esm.setUpElasticSearch();
		
		File teiFile = new File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110586v1.final.tei.xml");
		String tei = FileUtils.readFileToString(teiFile, "UTF-8");
		JSONObject json = JsonTapasML.toJSONObject(tei);
		String jsonStr = json.toString();
		try {
			jsonStr = indexingPreprocess.process(jsonStr);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		esm.index(jsonStr, "1");
		
		teiFile = new File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110668v1.final.tei.xml");
		tei = FileUtils.readFileToString(teiFile, "UTF-8");
		json = JsonTapasML.toJSONObject(tei);
		jsonStr = json.toString();
		try {
			jsonStr = indexingPreprocess.process(jsonStr);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		esm.index(jsonStr, "2");
	}
		
}