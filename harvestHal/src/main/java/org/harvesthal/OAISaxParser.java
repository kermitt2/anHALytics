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

    private boolean isConsideredType(String setSpec) {
        try {
            ConsideredTypes.valueOf(setSpec);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    enum ConsideredTypes {

        ART, COMM, OUV, POSTER, DOUV, PATENT, REPORT, THESE, HDR, LECTURE, COUV, OTHER, UNDEFINED
    };
    private boolean isCondideredType;
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
        if (qName.equals("record")) {
            counter++;
            if (isCondideredType) {
                teis.put(identifier, tei);
            }
            isCondideredType = false;
        } else if (qName.equals("error")) {
        } else if (qName.equals("identifier")) {
            identifier = getText().split(":")[2];
        } else if (qName.equals("datestamp")) {
            submission_date = getText();
        } else if (qName.equals("setSpec")) {
            setSpec = getText();
            String[] spec = setSpec.split(":");
            if ((spec.length > 1) && setSpec.split(":")[0].equals("type")) {
                isCondideredType = isConsideredType(setSpec.split(":")[1]);
            }
        } else if (qName.equals("ListRecords")) {
        } else if (qName.equals("responseDate")) {
        } else if (qName.equals("request")) {
        } else if (qName.equals("header")) {
        } else if (qName.equals("OAI-PMH")) {
        } else if (qName.equals("metadata")) {
        } else if (qName.equals("resumptionToken")) {
            // for OAI implementation
            token = getText();
            if (token != null) {
                if (token.length() < 2) {
                    token = null;
                }
            }
        } else {
            if (isCondideredType) {
                String text = getText();
                tei.append(text);
                tei.append("</").append(qName.split(":")[1]).append(">\n");
            }
        }
        accumulator.setLength(0);
    }

    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {

        if (qName.equals("ListRecords")) {
            token = null;
            teis = new HashMap<String, StringBuilder>();
            binaryUrls = new HashMap<String, String>();
        } else if (qName.equals("responseDate")) {
        } else if (qName.equals("request")) {
        } else if (qName.equals("error")) {
        } else if (qName.equals("record")) {
            tei = new StringBuilder();
        } else if (qName.equals("header")) {
        } else if (qName.equals("setSpec")) {
            collections = new ArrayList<String>();
        } else if (qName.equals("identifier")) {
        } else if (qName.equals("datestamp")) {
        } else if (qName.equals("OAI-PMH")) {
        } else if (qName.equals("resumptionToken")) {
        } else if (qName.equals("metadata")) {
        } else {
            if (isCondideredType) {
                qName = qName.split(":")[1];
                if (qName.equals("TEI")) {
                    tei.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                    tei.append("<TEI xmlns=\"http://www.tei-c.org/ns/1.0\" xmlns:hal=\"http://hal.archives-ouvertes.fr/\" > \n");
                } else {
                    int length = atts.getLength();
                    tei.append("<").append(qName);
                    for (int i = 0; i < length; i++) {
                        // Get names and values for each attribute
                        String name = atts.getQName(i);
                        String value = StringEscapeUtils.escapeXml(atts.getValue(i));
                        String attr = " " + name + "=\"" + value + "\"";
                        tei.append(attr);
                        //I could've used xslt..!
                        if (qName.contains("ref") && value.equals("file")) {
                            if (isCondideredType) {
                                binaryUrls.put(identifier, atts.getValue(i + 1));
                            }
                            processedPDF++;
                        }
                    }
                    tei.append(">\n");
                }
            }
        }
        accumulator.setLength(0);
    }
}
