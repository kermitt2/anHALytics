package org.harvesthal;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.*;
import org.slf4j.LoggerFactory;
import org.xml.sax.*;

public class OAIHarvester {

    private ArrayList<String> fields = null;
    private ArrayList<String> affiliations = null;

    private final MongoManager mongoManager;
    private final XmlFormatter xmlFormatter;

    private static int nullBinaries = 0;
    private static String tmpPath = null;

    private static Set<String> dates = new LinkedHashSet<String>();

    static {
        Calendar toDay = Calendar.getInstance();
        int todayYear = toDay.get(Calendar.YEAR);
        for (int year = todayYear; year >= 2000; year--) {
            for (int month = 12; month >= 1; month--) {
                for (int day = daysInMonth(year, month); day >= 1; day--) {
                    StringBuilder date = new StringBuilder();
                    date.append(String.format("%04d", year));
                    date.append("-");
                    date.append(String.format("%02d", month));
                    date.append("-");
                    date.append(String.format("%02d", day));
                    dates.add(date.toString());
                }
            }
        }
    }

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
                        if (tei.length() > 0) {
                            String formatedTei = xmlFormatter.format(tei.toString());
                            mongoManager.storeToGridfs(new ByteArrayInputStream(formatedTei.getBytes()), filename, teisNamespace);
                        } else
                            System.out.println("\t\t\t Tei not found !!!");
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
                        mongoManager.storeToGridfs(inTeiGrobid, filename + ".tei.xml", binariesNamespace);
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

        for (String date : dates) {
            harvestHALFromDate(date, false);
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
        String fromDate = args[1];
        OAIHarvester oai = new OAIHarvester();

        if (!dates.contains(fromDate)) {
            fromDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

            if (askConfirm()) {
                oai.harvestAllHAL();
            } else {
                return;
            }
        }
        oai.harvestHALFromDate(fromDate, true);
    }

    public static boolean askConfirm() {

        Scanner kbd = new Scanner(System.in);
        String decision = null;

        boolean yn = true;
        System.out.println("Are you sur you want to start harvesting all hal documents ? [yes]");
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

    private static int daysInMonth(int year, int month) {
        int daysInMonth;
        switch (month) {
            case 1:
            case 3:
            case 5:
            case 7:
            case 8:
            case 10:
            case 12:
                daysInMonth = 31;
                break;
            case 2:
                if (((year % 4 == 0) && (year % 100 != 0)) || (year % 400 == 0)) {
                    daysInMonth = 29;
                } else {
                    daysInMonth = 28;
                }
                break;
            default:
                // returns 30 even for nonexistant months 
                daysInMonth = 30;
        }
        return daysInMonth;
    }
}
