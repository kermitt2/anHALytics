/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
    private final XmlFormatter xmlFormatter;

    private static int nullBinaries = 0;
    private static String tmpPath = null;

    public OAIHarvester() {

        mongoManager = new MongoManager();
        xmlFormatter = new XmlFormatter();

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

    public void harvestHALFromDate(String date, boolean isStartDate) throws IOException, SAXException, ParserConfigurationException {
        for (String field : fields) {
            boolean stop = false;
            String tokenn = null;
            int loop = 0;
            while (!stop) {
                String request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&metadataPrefix=xml-tei&set=subject:" + field + "&set=collection:" + affiliations.get(0) + "&from=" + date + "&until=" + date;
                if (isStartDate) {
                    request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&metadataPrefix=xml-tei&set=subject:" + field + "&set=collection:" + affiliations.get(0) + "&from=" + date;
                }
                if (tokenn != null) {
                    request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&resumptionToken=" + tokenn;
                }

                String fileName = "oai-inria-" + field + "-" + loop + ".xml";
                String namespace = "HAL." + date;

                System.out.println("Sending: " + request);

                InputStream in = request(request);
                ArrayList<InputStream> ins = cloneInputStream(in, 2);
                in.close();

                mongoManager.storeToGridfs(ins.get(0), fileName, namespace);
                ins.get(0).close();

                OAISaxParser oaisax = parse(ins.get(1));
                ins.get(1).close();

                //teis
                System.out.println("\t Extracting teis.... for " + date);
                Map<String, StringBuilder> teis = oaisax.getTeis();
                String teisNamespace = namespace + ".tei";
                Iterator it1 = teis.entrySet().iterator();
                while (it1.hasNext()) {
                    Map.Entry pairsIdTei = (Map.Entry) it1.next();
                    try {
                        String filename = ((String) pairsIdTei.getKey()) + ".tei.xml";
                        System.out.println("\t\t Extracting tei.... for " + ((String) pairsIdTei.getKey()));
                        StringBuilder tei = (StringBuilder) pairsIdTei.getValue();
                        String formatedTei = xmlFormatter.format(tei.toString());
                        mongoManager.storeToGridfs(new ByteArrayInputStream(formatedTei.getBytes()), filename, teisNamespace);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                //binaries
                Map<String, String> urls = oaisax.getBinaryUrls();
                System.out.println("\t Downloading binaries.... for " + date);
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

                        System.out.println("\t\t Downloading: " + binaryUrl);//identifier + " as " + file.toString());
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
    }

    /**
     * Harvesting of all HAL repository
     */
    public void harvestAllHAL() throws IOException, SAXException, ParserConfigurationException {

        String date = null;

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

                date = "" + year + "-" + ((month < 10) ? "0" + month : month) + "-" + ((day < 10) ? "0" + day : day);

                harvestHALFromDate(date, false);

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
            if (request.endsWith("document") && !conn.getContentType().equals("application/pdf")) {
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
        if (args.length < 1) {
            System.err.println("usage: command process[harvest] from-date");
            return;
        }
        String process = args[0];
        if (!process.equals("harvest")) {
            System.err.println("unknown process: " + process);
            return;
        }
        String fromDate = null;
        if(args.length > 1){
            fromDate = args[1];
        }
        if(args.length > 0)
            fromDate = args[0];

        OAIHarvester oai = new OAIHarvester();
        if (fromDate == null) {
            if (askConfirm()) {
                oai.harvestAllHAL();
            } else {
                return;
            }

        } else {
            oai.harvestHALFromDate(fromDate, true);
        }
    }

    public static boolean askConfirm() {

        Scanner kbd = new Scanner(System.in);
        String decision = null;

        boolean yn = true;
        System.out.println("Are you sur you want to get all hal document ? [yes]");
        decision = kbd.nextLine();

        switch (decision) {
            case "yes":
                break;

            case "no":
                yn = false;
                break;

            default:
                break;
        }
        return yn;
    }
}
