package org.indexHal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import org.apache.commons.io.FileUtils;

import org.json.JsonTapasML;
import org.json.JSONArray;
import org.json.JSONObject;

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
		esm.setUpElasticSearch();
		
		File teiFile = new File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110586.final.tei.xml");
		String tei = FileUtils.readFileToString(teiFile, "UTF-8");
		JSONObject json = JsonTapasML.toJSONObject(tei);
		String jsonStr = json.toString();
		esm.index(jsonStr, "1");
		
		teiFile = new File(this.getResourceDir("src/test/resources/").getPath()+"/hal-01110586.final.tei.xml");
		tei = FileUtils.readFileToString(teiFile, "UTF-8");
		json = JsonTapasML.toJSONObject(tei);
		jsonStr = json.toString();
		esm.index(jsonStr, "2");
	}
		
}