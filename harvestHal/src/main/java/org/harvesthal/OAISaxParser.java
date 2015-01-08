package org.harvesthal;

import org.xml.sax.*;
import org.xml.sax.helpers.*;
import java.util.*;
import org.apache.commons.lang3.StringEscapeUtils;

/**
 * OAI-PMH harvesting of Open Access repositories. Metadata are stored in the
 * document DB. Optional download of full text files and consolidation via
 * CrossRef.
 *
 * Specific metadata format are used when available instead of limited Dublin
 * Core: actually xml-tei.
 *
 */
public class OAISaxParser extends DefaultHandler {

    private StringBuffer accumulator = new StringBuffer(); // Accumulate parsed text

    StringBuilder tei = new StringBuilder();
    private Map<String, StringBuilder> teis = new HashMap<String, StringBuilder>();
    
    private String setSpec = null;
    private String identifier = null;
    private String submission_date = null;
    private String file_url = null;

    private Map<String, String> binaryUrls = new HashMap<String, String>();
    private ArrayList<String> collections = new ArrayList<String>();

    private boolean isFileRef = false;

    public int counter = -1;
    public String token = null;
    private boolean download = false;

    public int processedPDF = 0;

    public OAISaxParser() {
    }

    public OAISaxParser(boolean down) {
        download = down;
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        accumulator.append(ch, start, length);
    }

    public Map<String, StringBuilder> getTeis() {
        return teis;
    }

    public Map<String, String> getBinaryUrls() {
        return binaryUrls;
    }

    public String getText() {
        return StringEscapeUtils.escapeXml(accumulator.toString().trim());
    }

    @Override
    public void endElement(java.lang.String uri, java.lang.String localName, java.lang.String qName) throws SAXException {
        switch (qName) {
            case "record":
                counter++;
                teis.put(identifier, tei);
                accumulator.setLength(0);
                break;
            case "error":
                accumulator.setLength(0);
                break;
            case "identifier":
                identifier = getText();
                accumulator.setLength(0);
                break;
            case "datestamp":
                submission_date = getText();
                accumulator.setLength(0);
                break;
            case "setSpec":
                setSpec = getText();
                accumulator.setLength(0);
                break;
            case "ListRecords":
                accumulator.setLength(0);
                break;
            case "responseDate":
                accumulator.setLength(0);
                break;
            case "request":
                accumulator.setLength(0);
                break;
            case "header":
                accumulator.setLength(0);
                break;
            case "OAI-PMH":
                accumulator.setLength(0);
                break;
            case "metadata":
                accumulator.setLength(0);
                break;
            case "resumptionToken":
                // for OAI implementation
                token = getText();
                if (token != null) {
                    if (token.length() < 2) {
                        token = null;
                    }
                }
                accumulator.setLength(0);
                break;
            default:
                String text = getText();
                tei.append(text);
                tei.append("</").append(qName.split(":")[1]).append(">\n");
                accumulator.setLength(0);
                break;
        }
    }

    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        switch (qName) {
            case "ListRecords": {
                token = null;
                teis = new HashMap<String, StringBuilder>();
                binaryUrls = new HashMap<String, String>();
                accumulator.setLength(0);
                break;
            }
            case "responseDate":
                accumulator.setLength(0);
                break;
            case "request":
                accumulator.setLength(0);
                break;
            case "error":
                accumulator.setLength(0);
                break;
            case "record":
                tei = new StringBuilder();
                accumulator.setLength(0);
                break;
            case "header":
                accumulator.setLength(0);
                break;
            case "setSpec":
                collections = new ArrayList<String>();
                accumulator.setLength(0);
                break;
            case "identifier":
                identifier = getText();
                accumulator.setLength(0);
                break;
            case "datestamp":
                accumulator.setLength(0);
                break;
            case "OAI-PMH":
                accumulator.setLength(0);
                break;
            case "resumptionToken":
                accumulator.setLength(0);
                break;
            case "metadata":
                accumulator.setLength(0);
                break;
            default: {
                if (qName.contains("TEI")) {
                    tei.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                    tei.append("<TEI xmlns=\"http://www.tei-c.org/ns/1.0\" xmlns:hal=\"http://hal.archives-ouvertes.fr/\" > \n");
                    break;
                }
                int length = atts.getLength();
                tei.append("<").append(qName.split(":")[1]);
                for (int i = 0; i < length; i++) {
                    // Get names and values for each attribute
                    String name = atts.getQName(i);
                    String value = StringEscapeUtils.escapeXml(atts.getValue(i));
                    String attr = " " + name + "=\"" + value + "\"";
                    tei.append(attr);
                    //I could've used xslt..!
                    if (qName.contains("ref") && value.equals("file")) {
                        binaryUrls.put(identifier, atts.getValue(i + 1));
                        processedPDF++;
                    }
                }
                tei.append(">\n");
                accumulator.setLength(0);
                break;
            }
        }
    }
}
