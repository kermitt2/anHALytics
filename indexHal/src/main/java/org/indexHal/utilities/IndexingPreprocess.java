package org.indexHal.utilities;

import org.json.*;
import org.codehaus.jackson.*;
import org.codehaus.jackson.node.*;
import org.codehaus.jackson.map.ObjectMapper;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Arrays;
import java.util.Properties;

import java.io.FileInputStream;
import java.net.*;
import java.io.*;
//import org.elasticsearch.common.io.Closeables;

/**
 *  Additional Java pre-processing of the JSON string.
 *
 *  @author PL
 */
public class IndexingPreprocess {
	
	// this is the list of elements for which the text nodes should be expanded with an additional json
	// node capturing the nesting xml:lang attribute name/value pair
	static public List<String> expandable = 
		Arrays.asList("$title", "$p", "$item", "$figDesc", "$head", "$meeting", "$div");
	
	public String collection = null;
	public String model_version = null;
	public String elasticsearch_host = null;
	public String elasticsearch_port = null;
	public String eclaTreeIndex = null;
	
	public IndexingPreprocess() {
		// read the relevant properties
		this.loadProperties();
	}
	
	private void loadProperties() {
		try {
			/*Properties prop = new Properties();
			File file = new File("es-plugin.properties");
			if (file.exists()) 
				prop.load(new FileInputStream("es-plugin.properties"));
			else
				prop.load(new FileInputStream("src/main/resources/es-plugin.properties"));
			
			collection = prop.getProperty("river.collection");
			model_version = prop.getProperty("river.document_model_version");
			elasticsearch_host = prop.getProperty("river.elasticsearch_host");
			elasticsearch_port = prop.getProperty("river.elasticsearch_port");
			eclaTreeIndex = prop.getProperty("river.eclaTreeIndex");*/
			
			/*collection = "patent";
			model_version = "1.3";
			elasticsearch_host = "localhost";
			elasticsearch_port = "9200";
			eclaTreeIndex = "eclatree";*/
		}
		catch (Exception e) {
			System.err.println("Failed to load properties: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public String process(String jsonStr) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode jsonRoot = mapper.readTree(jsonStr);
		// here recursive modification of the json document via Jackson
		jsonRoot = process(jsonRoot, mapper, null, false, false, false);
		
		return jsonRoot.toString();
	}
	
	private JsonNode process(JsonNode subJson,
						    ObjectMapper mapper, 
							String currentLang, 
					   		boolean fromArray, 
							boolean expandLang,
							boolean isDate) throws Exception {
		if (subJson.isContainerNode()) {
			if (subJson.isObject()) {
				Iterator<String> fields = ((ObjectNode)subJson).getFieldNames();
				JsonNode theSchemeNode = null;
				JsonNode theClassCodeNode = null;
				JsonNode theTypeNode = null;
				JsonNode thePersonNode = null;
				JsonNode theItemNode = null;
				JsonNode theKeywordsNode = null;
				JsonNode theDateNode = null;
				JsonNode theIdnoNode = null;	
				JsonNode theBiblScopeNode = null;	
				JsonNode theWhenNode = null;	
				JsonNode theTermNode = null;	
				while(fields.hasNext()) {
					String field = fields.next();
					
					if (field.startsWith("$")) {
						if ( expandable.contains(field) ) {
							expandLang = true;
						}
						else {
							expandLang = false;
						}
					}
					
					// ignoring the possibly present $lang_ nodes (this can appear in old version of JsonTapasML)
					if (field.startsWith("$lang_")) {
						// we need to ignore this node, as the expansion is added automatically when the textual element 
						// is reached
						JsonNode theChildNode = subJson.path(field);
						// the child should then always be an array with a unique textual child
						if (theChildNode.isArray()) {
							Iterator<JsonNode> ite = theChildNode.getElements();
							while (ite.hasNext()) {
								JsonNode temp = ite.next();
								theChildNode = temp;
								break;
							}
						}
						return process(theChildNode, mapper, currentLang, false, expandLang, false);
					}
					
					// we add the full name in order to index it directly without more time consuming
					// script and concatenation at facet creation time
					if (field.equals("$persName")) {
						JsonNode theChild = subJson.path("$persName");
						// this child is an array
						String fullName = null;
						if (theChild.isArray()) {
							String forename = null;
							String surname = null;
							Iterator<JsonNode> ite = theChild.getElements();
							while (ite.hasNext()) {
								JsonNode temp = ite.next();
								if (temp.isObject()) {
									Iterator<String> subfields = ((ObjectNode)temp).getFieldNames();

									while(subfields.hasNext()) {
										String subfield = subfields.next();
										if (subfield.equals("$forename")) {
											// get the text value of the array
											Iterator<JsonNode> ite2 = temp.path(subfield).getElements();
											while (ite2.hasNext()) {
												JsonNode temp2 = ite2.next();											
											
												if (forename != null) {
													forename += " " + temp2.getTextValue();
												}
												else {
													forename = temp2.getTextValue();
												}
												break;
											}
										}
										else if (subfield.equals("$surname")) {
											// get the text value of the array
											Iterator<JsonNode> ite2 = temp.path(subfield).getElements();
											while (ite2.hasNext()) {
												JsonNode temp2 = ite2.next();											
												surname = temp2.getTextValue();
												break;
											}
										}
									}
								}
							}
							if (forename != null) {
								fullName = forename;
							}
							if (surname != null) {
								fullName += " " + surname;
							}
														
							if (fullName != null) {
								fullName = fullName.trim();
								JsonNode newNode = mapper.createObjectNode(); 
								JsonNode textNode = mapper.createArrayNode();						
								JsonNode tnode = new TextNode(fullName);
								((ArrayNode)textNode).add(tnode);	
								((ObjectNode)newNode).put("$fullName",textNode);
								((ArrayNode)theChild).add(newNode);
							}
						}
						return subJson;
					}
					else if (field.equals("$classCode")) {
						theClassCodeNode = subJson.path("$classCode");
					}
					else if (field.equals("$person")) {
						thePersonNode = subJson.path("$person");
					}
					else if (field.equals("$item")) {
						theItemNode = subJson.path("$item");
					}
					else if (field.equals("$date")) {
						theDateNode = subJson.path("$date");
					}
					else if (field.equals("$keywords")) {
						theKeywordsNode = subJson.path("$keywords");
					}
					else if (field.equals("$idno")) {
						theIdnoNode = subJson.path("$idno");
					}
					else if (field.equals("$biblScope")) {
						theBiblScopeNode = subJson.path("$biblScope");
					}					
					else if (field.equals("scheme")) {
						theSchemeNode = subJson.path("scheme");
					}
					else if (field.equals("type")) {
						theTypeNode = subJson.path("type");
					}
					else if (field.equals("when")) {
						theWhenNode = subJson.path("when");
					}
					else if (field.equals("$term")) {
						theTermNode = subJson.path("$term");
					}
					else if (field.equals("xml:lang")) {
						JsonNode theNode = subJson.path("xml:lang");
						currentLang = theNode.getTextValue();
					}
					else if (field.equals("lang")) {
						JsonNode theNode = subJson.path("lang");
						currentLang = theNode.getTextValue();
					}
					
					// TODO filter all the fields starting by _ (e.g. _rev), except _id
				}
				if ( (theSchemeNode != null) && (theClassCodeNode != null) ) {
					JsonNode schemeNode = mapper.createObjectNode();
					((ObjectNode) schemeNode).put("$scheme_"+theSchemeNode.getTextValue(),
									process(theClassCodeNode, mapper, currentLang, false, expandLang, false));
					JsonNode arrayNode = mapper.createArrayNode();		
					((ArrayNode) arrayNode).add(schemeNode);
					((ObjectNode) subJson).put("$classCode", arrayNode); // update value
					return subJson;
				}
				else if ( (theTypeNode != null) && (thePersonNode != null) ) {
					JsonNode typeNode = mapper.createObjectNode();
					((ObjectNode) typeNode).put("$type_"+theTypeNode.getTextValue(),
									process(thePersonNode, mapper, currentLang, false, expandLang, false));
					JsonNode arrayNode = mapper.createArrayNode();		
					((ArrayNode) arrayNode).add(typeNode);
					((ObjectNode) subJson).put("$person", arrayNode); // update value
					return subJson;
				}
				else if ( (theTypeNode != null) && (theItemNode != null) ) {
					JsonNode typeNode = mapper.createObjectNode();
					((ObjectNode) typeNode).put("$type_"+theTypeNode.getTextValue(),
									process(theItemNode, mapper, currentLang, false, expandLang, false));
					JsonNode arrayNode = mapper.createArrayNode();		
					((ArrayNode) arrayNode).add(typeNode);
					((ObjectNode) subJson).put("$item", arrayNode); // update value
					return subJson;
				}
				/*else if ( (theTypeNode != null) && (theDateNode != null) ) {
					JsonNode typeNode = mapper.createObjectNode();
					((ObjectNode) typeNode).put("$type_"+theTypeNode.getTextValue(),
									process(theDateNode, mapper, currentLang, false, expandLang, true));
					JsonNode arrayNode = mapper.createArrayNode();	
					((ArrayNode) arrayNode).add(typeNode);
					((ObjectNode) subJson).put("$date", arrayNode); // update value
					return subJson;
				}*/
				else if ( (theTypeNode != null) && (theKeywordsNode != null) ) {
					JsonNode typeNode = mapper.createObjectNode();
					((ObjectNode) typeNode).put("$type_"+theTypeNode.getTextValue(),
									process(theKeywordsNode, mapper, currentLang, false, expandLang, false));
					JsonNode arrayNode = mapper.createArrayNode();		
					((ArrayNode) arrayNode).add(typeNode);
					((ObjectNode) subJson).put("$keywords", arrayNode); // update value
					return subJson;
				}
				else if ( (theTypeNode == null) && (theKeywordsNode != null) ) {
					// we need to set a default "author" type 
					JsonNode typeNode = mapper.createObjectNode();
					((ObjectNode) typeNode).put("$type_author",
									process(theKeywordsNode, mapper, currentLang, false, expandLang, false));
					JsonNode arrayNode = mapper.createArrayNode();		
					((ArrayNode) arrayNode).add(typeNode);
					((ObjectNode) subJson).put("$keywords", arrayNode); // update value
					return subJson;
				}
				else if ( (theTypeNode != null) && (theIdnoNode != null) ) {
					JsonNode typeNode = mapper.createObjectNode();
					((ObjectNode) typeNode).put("$type_"+theTypeNode.getTextValue(),
									process(theIdnoNode, mapper, currentLang, false, expandLang, false));
					JsonNode arrayNode = mapper.createArrayNode();		
					((ArrayNode) arrayNode).add(typeNode);
					((ObjectNode) subJson).put("$idno", arrayNode); // update value
					return subJson;
				}
				else if ( (theTypeNode != null) && (theBiblScopeNode != null) ) {
					JsonNode typeNode = mapper.createObjectNode();
					((ObjectNode) typeNode).put("$type_"+theTypeNode.getTextValue(),
									process(theBiblScopeNode, mapper, currentLang, false, expandLang, false));
					JsonNode arrayNode = mapper.createArrayNode();		
					((ArrayNode) arrayNode).add(typeNode);
					((ObjectNode) subJson).put("$biblScope", arrayNode); // update value
					return subJson;
				}
				/*else if ( (theTermNode != null) && (theTypeNode != null) ) {
					String localVal = theTypeNode.getTextValue();
					if ((localVal != null) && (localVal.equals("classification-symbol")) ) {
						
						JsonNode theChildNode = null;
						if (theTermNode.isArray()) {
							Iterator<JsonNode> ite = theTermNode.getElements();
							while (ite.hasNext()) {
								JsonNode temp = ite.next();
								theChildNode = temp;
								break;
							}
						}
						String localClass = null;
						
						if (theChildNode != null) 
							localClass = theChildNode.getTextValue();
							
						if ((localClass != null) && (localClass.indexOf(":") == -1)) {
							// we have an ECLA class
							
							// all expansions will be put under a container which can handle more
							// than one class (this is due to the possible compact representation)
							JsonNode containerNode = mapper.createObjectNode();
							
							// handle old-ages compact form
							boolean trace = false;
							if (localClass.indexOf("+") != -1) {
								System.out.print(localClass + ": ");
								trace = true;
							}
							List<String> classes = normalizeECLAClass(localClass);
							
							for (String localClas : classes) {
								if (trace) {
									System.out.print(" " + localClas);
								} 
								
								List<String> paths = giveECLARootPath(localClas);
								String rootPath = "";
								for (String theStep : paths) {
									rootPath += " " + theStep;
								}
								rootPath = rootPath.trim();
								JsonNode tnode = new TextNode(rootPath);
								JsonNode arrayNode = mapper.createArrayNode();	
								((ArrayNode) arrayNode).add(tnode);
								((ObjectNode) containerNode).put("$path", arrayNode); 
								((ObjectNode) containerNode).put("term", localClas); 
								// we finally also add an expansion for the level specific statistics
								// here at the indexing level we can simply use the path to specify the level
								String[] steps = rootPath.split(" ");
								JsonNode objectNode = mapper.createObjectNode();
								
								for(int i=0; i<steps.length; i++) {
									String label = null;
									if (i == 0) {
										label = "class";
									}
									else if (i == 1) {
										label = "subclass";
									}
									else if (i == 2) {
										label = "group";
									}
									else if (i == 3) {
										label = "subgroup";
									}
									else {
										label = "ecla-" + (i-4);
									}
								
									((ObjectNode) objectNode).put(label, steps[i]);
								}
								((ObjectNode) containerNode).put("$levels", objectNode); 
							}
							if (trace) {
								System.out.println("");
							}
							((ObjectNode) subJson).put("$ecla", containerNode); 
						}
						else if (localClass != null) { 
							// we have an ICO code
							((ObjectNode) subJson).put("ico", localClass); 
						}						
						
					}
					return subJson;
				}*/
			}
			JsonNode newNode = null;
			if (subJson.isArray()) {
				newNode = mapper.createArrayNode(); 
				Iterator<JsonNode> ite = subJson.getElements();
				while (ite.hasNext()) {
					JsonNode temp = ite.next();
					((ArrayNode)newNode).add(process(temp, mapper, currentLang, true, expandLang, isDate));
				}
			}
			else if (subJson.isObject()) {
				newNode = mapper.createObjectNode(); 
				Iterator<String> fields = subJson.getFieldNames();
				while(fields.hasNext()) {
					String field = fields.next();
					if (field.equals("$date") || field.equals("when")) {
						((ObjectNode)newNode).put(field,process(subJson.path(field), mapper, 
							currentLang, false, expandLang, true));
					}
					else {
						((ObjectNode)newNode).put(field,process(subJson.path(field), mapper, 
							currentLang, false, expandLang, false));
					}
				}
			}
			return newNode;
		}
		else if (subJson.isTextual() && fromArray && expandLang) {
			JsonNode langNode = mapper.createObjectNode(); 
			String langField = "$lang_";
			if (currentLang == null) {
				langField += "unknown";
			}
			else {
				langField += currentLang;
			}
			ArrayNode langArrayNode = mapper.createArrayNode(); 
			langArrayNode.add(subJson);
			((ObjectNode)langNode).put(langField, langArrayNode);
			return langNode;
		}
		else if (subJson.isTextual() && isDate) {
			String val = null;
			if (subJson.getTextValue().length() == 4) {
				val = subJson.getTextValue() + "-12-31";
			}
			else if ( (subJson.getTextValue().length() == 7) || (subJson.getTextValue().length() == 6) ) {
				int ind = subJson.getTextValue().indexOf("-");
				String month = subJson.getTextValue().substring(ind+1,subJson.getTextValue().length());
				if (month.length() == 1) {
					month = "0" + month;
				}
				if (month.equals("02")) {
					val = subJson.getTextValue().substring(0,4) + "-" + month + "-28";
				}
				else if ((month.equals("04")) || (month.equals("06")) || (month.equals("09")) 
					|| (month.equals("11"))) {
					val = subJson.getTextValue().substring(0,4) + "-" + month + "-30";
				}
				else {
					val = subJson.getTextValue().substring(0,4) + "-" + month + "-31";
				}
			}
			else {
				val = subJson.getTextValue();
			}
			JsonNode tnode = new TextNode(val);
			return tnode;
		}
		else {
			return subJson;
		}
	}
	
	static private String queryECLA = 
		"{\"fields\":[\"rootPath\", \"_id\"],\"query\":{\"query_string\":{\"query\": \"symbol:%CLASS%\"}}}";
	
	/**
	 *  For a given ECLA class, give the full root path, from the root down to the current class
	 */
	public List<String> giveECLARootPath(String eclaClass) {
		// This is done via the ECLA tree index in ElasticSearch
		String query = queryECLA.replace("%CLASS%",eclaClass);
		List<String> result = new ArrayList<String>();	
		HttpURLConnection connection = null;
        InputStream is = null;
        try {
			// we send a post request to elasticsearch to get the root path
			URL url = new URL("http://" + elasticsearch_host+":"+elasticsearch_port+"/"+eclaTreeIndex + "/_search");
            connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

			DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
			wr.writeBytes(query);
			wr.flush();
			wr.close();

            is = connection.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                // we put here, so we block if there is no space to add
				int ind = line.indexOf("ECLARoot");
				if (ind != -1) {
					int ind2 = line.indexOf(" ", ind+1);
					if (ind2 == -1) {
						continue;
					}
					int ind3 = line.indexOf("\"", ind2+1);
					if (ind3 == -1) {
						continue;
					}
					String subLine = line.substring(ind2, ind3);
					StringTokenizer st = new StringTokenizer(subLine, " "); 
					while(st.hasMoreTokens()) {
						result.add(st.nextToken());
					}
				}
				else {
					continue;
				}
            }
        } 
		catch (Exception e) {
            //Closeables.closeQuietly(is);
            if (connection != null) {
                try {
                    connection.disconnect();
                } 
				catch (Exception e1) {
                    e1.printStackTrace();
                } 
				finally {
                    connection = null;
                }
            }
			e.printStackTrace();
		}
		return result;
	}
	
	/** 
	 *  This method provides a basic handling of the ECLA classes expressed in a compact way
	 *  with a "+" symbol.
	 *  This might over-generate the possible ECLA classes, so a filter based on the possible
	 *  valid ECLA classes is necessary.
	 */
	public List<String> normalizeECLAClass(String classs) {
		List<String> eclas = new ArrayList<String>();
		if (classs.indexOf('+') != -1) {
			StringTokenizer st = new StringTokenizer(classs, "+");
			String firstClass = null;
			while(st.hasMoreTokens()) {	
				String clas = st.nextToken();		 
				if (firstClass == null) {
					firstClass = clas;
					eclas.add(firstClass);
					//break;
				}
				else {
					int length = clas.length();
					if (length < 7) {
						if (!eclas.contains(firstClass+clas))
							eclas.add(firstClass+clas);
						int index = clas.indexOf('/');
						if (index != -1) {
							eclas.add(firstClass.substring(0, index+1)+clas);
						}
   						clas = firstClass.substring(0, firstClass.length() - length) + clas;
					}
					
					if (!eclas.contains(clas)) {
   						eclas.add(clas);
   					}
				}
			}
		}
		else {
			eclas.add(classs);
		}
		return eclas;
	}
	
	
} 