package org.harvesthal;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.*;
import org.xml.sax.*;

public class OAIHarvester {

    private ArrayList<String> fields = null;
    private ArrayList<String> affiliations = null;
    
    private final MongoManager mongoManager;    
    private static int nullBinaries = 0;
    private static String tmpPath = null;

    public OAIHarvester() {

        mongoManager = new MongoManager();

        fields = new ArrayList<String>();
        affiliations = new ArrayList<String>();
        
        // we store the main HAL scientific fields for a basic organization 
        //fields.add("CHIM"); 
        //fields.add("SCCO"); 
        fields.add("info"); // info
        //fields.add("SPI"); 
        //fields.add("SDV"); // life science
        //fields.add("MATH"); 
        //fields.add("NLIN"); 
        //fields.add("QFIN"); 
        //fields.add("STAT");
        //fields.add("phys"); // physique
        
        affiliations.add("INRIA");
        
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("harvestHal.properties"));
        } catch (Exception e) {
            e.printStackTrace();
        } 
        tmpPath = prop.getProperty("harvestHal.tmpPath");
    }

    /**
     * Harvesting of HAL repository
     */
    public void harvestHAL() throws IOException, SAXException, ParserConfigurationException {

        XmlFormatter xmlFormatter = new XmlFormatter();

        String dateFrom = null;
        String dateUntil = null;

        Calendar toDay = Calendar.getInstance();
        int year = toDay.get(Calendar.YEAR); // this is the current year... then we go back in time
        int month = toDay.get(Calendar.MONTH) + 1; // we start one month in the future... and then go back in time
        int day = 0;

        while (year >= 2014) {
            // limit year for going back in time (these are the publication year, not 
            //the submission ones)
            if (month == 0) {
                month = 12;
                year--;
                continue;
            }
            if ((month == 1) || (month == 3) || (month == 5) || (month == 7) || (month == 8)
                    || (month == 10) || (month == 12)) {
                day = 31;
            } else if (month == 2) {
                day = 28;
            } else {
                day = 30;
            }

            while (day > 0) {

                dateFrom = "" + year + "-" + ((month < 10) ? "0" + month : month) + "-" + ((day < 10) ? "0" + day : day);
                dateUntil = "" + year + "-" + ((month < 10) ? "0" + month : month) + "-" + ((day < 10) ? "0" + day : day);

                for (String field : fields) {
                    boolean stop = false;
                    String tokenn = null;
                    int loop = 0;
                    while (!stop) {

                        String request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&metadataPrefix=xml-tei&set=subject:" + field + "&set=collection:"+affiliations.get(0)+"&from=" + dateFrom + "&until=" + dateUntil;
                        if (tokenn != null) {
                            request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&resumptionToken=" + tokenn;
                        }

                        String fileName = "oai-inria-" + field + "-" + loop + ".xml";
                        String namespace = "HAL." + year + "." + month + "." + day;

                        System.out.println("Sending: " + request);

                        InputStream in = request(request);
                        ArrayList<InputStream> ins = cloneInputStream(in, 2);
                        in.close();

                        mongoManager.storeToGridfs(ins.get(0), fileName, namespace);
                        ins.get(0).close();

                        OAISaxParser oaisax = parse(ins.get(1));
                        ins.get(1).close();

                        //teis
                        System.out.println("Downloading teis.... for " + dateUntil);
                        Map<String, StringBuilder> teis = oaisax.getTeis();
                        String teisNamespace = namespace + ".tei";
                        Iterator it1 = teis.entrySet().iterator();
                        while (it1.hasNext()) {
                            Map.Entry pairsIdTei = (Map.Entry) it1.next();
                            try {
                                String filename = ((String) pairsIdTei.getKey()) + ".tei.xml";
                                StringBuilder tei = (StringBuilder) pairsIdTei.getValue();
                                String formatedTei = xmlFormatter.format(tei.toString());
                                mongoManager.storeToGridfs(new ByteArrayInputStream(formatedTei.getBytes()), filename, teisNamespace);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        //binaries
                        Map<String, String> urls = oaisax.getBinaryUrls();
                        System.out.println("Downloading binaries.... for " + dateUntil);
                        String binariesNamespace = namespace + ".documents";
                        Iterator it2 = urls.entrySet().iterator();
                        InputStream inTeiGrobid = null;
                        InputStream inBinary = null;
                        while (it2.hasNext()) {
                            Map.Entry pairs = (Map.Entry) it2.next();
                            try {
                                String filename = (String) pairs.getKey();
                                String binaryUrl = (String) pairs.getValue();

                                inBinary = new BufferedInputStream(request(binaryUrl));

                                System.out.println("Downloading: " + binaryUrl);//identifier + " as " + file.toString());
                                String tmpFilePath = storeTmpFile(inBinary);
                                inBinary.close();

                                String tei = getTeiFromBinary(tmpFilePath);

                                inTeiGrobid = new ByteArrayInputStream(tei.getBytes());
                                mongoManager.storeToGridfs(inTeiGrobid, filename + ".tei", binariesNamespace);
                                mongoManager.storeToGridfs(tmpFilePath, filename + ".pdf", binariesNamespace);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        // token if any:

                        tokenn = oaisax.token;
                        if (tokenn == null) {
                            stop = true;
                        } else {
                            loop++;
                        }
                    }
                }

                day--;
            }
            month--;
        }
    }

    /**
     * Download binaries from a given URL
     */
    public static void getBinaryURLContent(File file, InputStream in) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        DataOutputStream writer = new DataOutputStream(fos);
        try {
            byte[] buf = new byte[4 * 1024]; // 4K buffer
            int bytesRead;
            while ((bytesRead = in.read(buf)) != -1) {
                writer.write(buf, 0, bytesRead);
            }
        } finally {
            in.close();
        }
    }

    public static String getTeiFromBinary(String filePath) throws IOException {
        String tei = Grobid.runGrobid(filePath);
        return tei;
    }

    public static String storeTmpFile(InputStream inBinary) throws IOException {
        File f = File.createTempFile("tmp", ".pdf", new File(tmpPath));
        // deletes file when the virtual machine terminate
        f.deleteOnExit();
        String filePath = f.getAbsolutePath();
        getBinaryURLContent(f, inBinary);
        return filePath;
    }

    private static OAISaxParser parse(InputStream is) throws IOException, SAXException, ParserConfigurationException {
        OAISaxParser oaisax = new OAISaxParser();
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        //get a new instance of parser
        SAXParser parser = spf.newSAXParser();
        parser.parse(is, oaisax);
        return oaisax;
    }

    public static ArrayList<InputStream> cloneInputStream(InputStream in, int howMany) throws IOException {
        ArrayList<InputStream> iss = new ArrayList<InputStream>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Fake code simulating the copy (inputStream get consumed)
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) > -1) {
            baos.write(buffer, 0, len);
        }

        // Open new InputStreams using the recorded bytes
        for (int i = 0; i < howMany; i++) {
            iss.add(new ByteArrayInputStream(baos.toByteArray()));
        }
        baos.flush();
        baos.close();
        return iss;
    }

    public static InputStream request(String request) {
        InputStream in = null;
        try {
            URL url = new URL(request);
            URLConnection conn = url.openConnection();
            if(request.endsWith("document") && !conn.getContentType().equals("application/pdf")){
                nullBinaries++;
                throw new BinaryNotAvailableException("Cannot download pdf file, because input file is null.");
            }
            in = conn.getInputStream();
            return in;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return in;
    }

    public static void main(String[] args)
            throws IOException, SAXException, ParserConfigurationException {
        OAIHarvester oai = new OAIHarvester();
        oai.harvestHAL();
    }
}
