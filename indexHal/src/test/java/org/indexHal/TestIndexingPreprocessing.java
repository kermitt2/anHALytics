package org.indexHal.utilities;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import org.json.*;
import org.codehaus.jackson.*;
import org.codehaus.jackson.node.*;
import org.codehaus.jackson.map.ObjectMapper;

import java.util.List;

/**
 *
 */
public class TestIndexingPreprocessing {

	//@Test
    public void testIndexingPreprocessing() throws Exception {
		IndexingPreprocess indexingPreprocess = new IndexingPreprocess();
		
        String jsonInput = "{\"$textClass\":[{\"scheme\":\"ipcr\",\"$classCode\":[{\"$idno\":[\"C01B   3/30        20060101A I20051008RMEP\"],";
		jsonInput += "\"type\":\"text\"}],\"rend\":\"1\"},{\"scheme\":\"ipc\",\"$classCode\":[" +
		             "{\"$idno\":[\"C10G   9/32        20060101A I20051008RMEP\"],\"type\":\"text\"}]," +
		             "\"rend\":\"2\"}],\"type\":\"classifications-ipcr\"}";

		String jsonOutput = indexingPreprocess.process(jsonInput);
		
		System.out.println("in:  " + jsonInput + "\n");
		System.out.println("out: " + jsonOutput + "\n\n");
		
		jsonInput = "{\"$title\":[{\"tagName\":\"header\"},\"bli\",{\"$body\":[\"bla & blo\",{\"num\":\"2\",\"$p\":[\"blabla\"],"
		  + "\"xml:lang\":\"fr\",\"type\":\"abstract\"},\"blo <bli>\"]},{\"tagName\":\"back\",\"xml:lang\":\"de\","
		  + "\"type\":\"reference\"},\"blibli\",{\"$pipo\":[{\"tagName\":\"truc\"}],\"xml:lang\":\"be\"},\"blibli&bli\"],\"xml:lang\":\"en\"}";
		
		jsonOutput = indexingPreprocess.process(jsonInput);

		System.out.println("in:  " + jsonInput + "\n");
		System.out.println("out: " + jsonOutput);

		jsonInput = "{\"$publicationStmt\":[{\"when\":\"1974\",\"$date\":[\"1974-02\"],\"type\":\"published\"}],\"type\":\"submission\",\"$note\":[\"1974-01\"]}";

		jsonOutput = indexingPreprocess.process(jsonInput);

		System.out.println("in:  " + jsonInput + "\n");
		System.out.println("out: " + jsonOutput + "\n");
		
		// test if the existing $lang_ nodes are ignored
		jsonInput = "{\"$publicationBla\":[{\"$lang_en\": [\"Bla bla blo\"]}],\"type\":\"submission\","+
			"\"$note\":[{\"$lang_fr\":[\"merdre\"]}]}";

		System.out.println("in:  " + jsonInput+ "\n");
		jsonOutput = indexingPreprocess.process(jsonInput);
		System.out.println("out: " + jsonOutput+ "\n");

		jsonInput = "{\"$titleStmt\": [ { \"$title\": [ \"A RE-AIM evaluation of evidence-based multi-level interventions to improve obesity-related behaviours in adults: a systematic review (the SPOTLIGHT project)\" ], \"xml:lang\": \"en\", \"xml:id\": \"_FUznWJZ\"}]}";
		System.out.println("in:  " + jsonInput+ "\n");
		jsonOutput = indexingPreprocess.process(jsonInput);
		System.out.println("out: " + jsonOutput+ "\n");
		
    }

	//@Test
    public void testAuthorExpansion() throws Exception {
		IndexingPreprocess indexingPreprocess = new IndexingPreprocess();
		
		String jsonInput = "{\"$author\":[{\"$persName\":[{\"$forename\":[\"Jerome\"],\"type\":\"first\"},"+
		                   "{\"$surname\":[\"Le Noir\"]}]},{\"$affiliation\":[{\"$orgName\":[\"THALES\"],"+
						   "\"type\":\"institution\"},{\"$address\":[{\"$addrLine\":"+
			   "[\"Route départementale 128, 91120 Palaiseau\"]},{\"$country\":[\"France\"],\"key\":\"FR\"}]}]}]}";
		System.out.println("\nin:  " + jsonInput);
		String jsonOutput = indexingPreprocess.process(jsonInput);;
		System.out.println("out: " + jsonOutput);
	}
	
	//@Test
    public void testDefaultKeywordsExpansion() throws Exception {
		IndexingPreprocess indexingPreprocess = new IndexingPreprocess();
	
		String jsonInput = "{\"$profileDesc\":[{\"$keywords\":[\"bla\"]}]}";
		System.out.println("\nin:  " + jsonInput);
		String jsonOutput = indexingPreprocess.process(jsonInput);;
		System.out.println("out: " + jsonOutput);
		System.out.print("\n");
	}
	
}
