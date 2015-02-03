package org.harvesthal;

import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.xml.parsers.*;
import javax.xml.transform.TransformerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.*;

public class OAIHarvester {

    private static final Logger logger = LoggerFactory.getLogger(OAIHarvester.class);

    private static final int NTHREDS = 10;
    private ArrayList<String> fields = null;
    private ArrayList<String> affiliations = null;

    private final MongoManager mongoManager;
    private final XmlFormatter xmlFormatter;
    private Grobid grobidProcess = null;

    private static int nullBinaries = 0;
    private static String tmpPath = null;

    private String grobid_host = null;
    private String grobid_port = null;

    public enum Decision {

        yes, no
    }

    private static Set<String> dates = new LinkedHashSet<String>();

    static {
        Calendar toDay = Calendar.getInstance();
        int todayYear = toDay.get(Calendar.YEAR);
        int todayMonth = toDay.get(Calendar.MONTH) + 1;
        for (int year = todayYear; year >= 2000; year--) {
            int monthYear = (year == todayYear) ? todayMonth : 12;
            for (int month = monthYear; month >= 1; month--) {
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

        affiliations.add("INRIA");

        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("harvestHal.properties"));
            grobid_host = prop.getProperty("harvestHal.grobid_host");
            grobid_port = prop.getProperty("harvestHal.grobid_port");
        } catch (Exception e) {
            e.printStackTrace();
        }
        tmpPath = prop.getProperty("harvestHal.tmpPath");
    }

    public void harvestHALForDate(String date) throws IOException, SAXException, ParserConfigurationException, ParseException {
        boolean stop = false;
        String tokenn = null;
        int loop = 0;
        while (!stop) {
            //String request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&metadataPrefix=xml-tei&set=subject:" + field + "&set=collection:" + affiliations.get(0) + "&from=" + date + "&until=" + date;
            String request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&metadataPrefix=xml-tei&from=" + date + "&until=" + date;

            if (tokenn != null) {
                request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&resumptionToken=" + tokenn;
            }
            String fileName = "oai-inria-" + loop + ".xml";

            logger.debug("Sending: " + request);

            InputStream in = request(request);
            String tmpXmlPath = storeToTmpXmlFile(in);
            File xmlFile = new File(tmpXmlPath);

            //mongoManager.storeToGridfs(tmpXmlPath, fileName, MongoManager.OAI_NAMESPACE, date);
            OAISaxParser oaisax = parse(xmlFile);

            //teis
            logger.debug("\t Extracting teis.... for " + date);
            Map<String, StringBuilder> teis = oaisax.getTeis();
            Map<String, String> urls = oaisax.getBinaryUrls();
            Iterator it1 = teis.entrySet().iterator();
            while (it1.hasNext()) {
                Map.Entry pairsIdTei = (Map.Entry) it1.next();
                String id = (String) pairsIdTei.getKey();
                try {
                    String teiFilename = id + ".tei.xml";
                    logger.debug("\t\t Extracting tei.... for " + id);
                    StringBuilder tei = (StringBuilder) pairsIdTei.getValue();
                    if (tei.length() > 0) {
                        String formatedTei = xmlFormatter.format(tei.toString());
                        logger.debug("\t\t\t\t Storing tei : " + id);
                        mongoManager.storeToGridfs(new ByteArrayInputStream(formatedTei.getBytes()), teiFilename, MongoManager.OAI_TEI_NAMESPACE, date);

                        //binary processing.
                        if (urls.containsKey(id)) {
                            String binaryUrl = urls.get(id);
                            logger.debug("\t\t\t Downloading: " + binaryUrl);
                            InputStream inBinary = new BufferedInputStream(request(binaryUrl));
                            //String tmpFilePath = storeTmpFile(inBinary);
                            System.out.println((String) pairsIdTei.getKey() + ".pdf");
                            mongoManager.storeToGridfs(inBinary, (String) pairsIdTei.getKey() + ".pdf", MongoManager.BINARY_NAMESPACE, date);
                            inBinary.close();

                            /*logger.debug("\t\t\t Grobid processing...");
                             String grobidTei = getTeiFromBinary(tmpFilePath);
                             InputStream inTeiGrobid = new ByteArrayInputStream(grobidTei.getBytes());
                             mongoManager.storeToGridfs(inTeiGrobid, teiFilename, MongoManager.GROBID_NAMESPACE, date);                  
                             inTeiGrobid.close();
                             */
                        } else {
                            logger.debug("\t\t\t PDF not found !");
                        }
                    } else {
                        logger.debug("\t\t\t Tei not found !!!");
                    }
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

    /**
     * Harvesting of all HAL repository
     */
    public void harvestAllHAL() throws IOException, SAXException, ParserConfigurationException, ParseException {
        for (String date : dates) {
            harvestHALForDate(date);
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
    /*
     public String getTeiFromBinary(String filePath) throws IOException {
     String tei = grobidProcess.runFullTextGrobid(filePath, 2, -1, true);
     tei = tei.replace("&amp\\s+;", "&amp;");
     return tei;
     }
     */

    public static String storeTmpFile(InputStream inBinary) throws IOException {
        File f = File.createTempFile("tmp", ".pdf", new File(tmpPath));
        // deletes file when the virtual machine terminate
        f.deleteOnExit();
        String filePath = f.getAbsolutePath();
        getBinaryURLContent(f, inBinary);
        return filePath;
    }

    public static String storeToTmpXmlFile(InputStream inBinary) throws IOException {
        File f = File.createTempFile("tmp", ".xml", new File(tmpPath));
        // deletes file when the virtual machine terminate
        f.deleteOnExit();
        String filePath = f.getAbsolutePath();
        getBinaryURLContent(f, inBinary);
        return filePath;
    }

    private static OAISaxParser parse(File file) throws IOException, SAXException, ParserConfigurationException {
        OAISaxParser oaisax = new OAISaxParser();
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        //get a new instance of parser
        SAXParser parser = spf.newSAXParser();
        parser.parse(file, oaisax);
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
                logger.debug("\t\t\t Cannot download pdf file, because input file is null.");
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
            throws IOException, SAXException, ParserConfigurationException, ParseException, TransformerException {
        OAIHarvester oai = new OAIHarvester();
        if (args.length == 0) {
            System.out.println(getHelp());
        } else {
            String currArg;
            for (int i = 0; i < args.length; i++) {
                currArg = args[i];
                if (currArg.equals("-h")) {
                    System.out.println(getHelp());
                    break;
                }
                if (currArg.equals("-exe")) {
                    final String process = args[i + 1];
                    if (process.equals("harvestAll")) {
//                        if (askConfirm()) {
                        oai.harvestAllHAL();
                        //                      } else {
                        //                         return;
                        //                    }
                    } else if (process.equals("harvestDaily")) {
                        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                        Date date = new Date();
                        oai.harvestHALForDate(dateFormat.format(date));
                    } else if (process.equals("processGrobid")) {
                        //clearTmpDirectory();
                        Map<String, List<String>> filenames = oai.loadFiles(MongoManager.BINARY_NAMESPACE);
                        oai.processGrobid(filenames);
                    } else if (process.equals("merge")) {
                        Map<String, List<String>> filenames = oai.loadFiles(MongoManager.GROBID_TEI_NAMESPACE);
                        oai.merge(filenames);
                    } else {
                        System.err.println("-exe value should be one value from [harvestDaily | harvestAll] ");
                        break;
                    }

                }
            }
        }
    }

    private static void clearTmpDirectory() throws IOException {
        File tmpDirectory = new File(tmpPath);
        if (!tmpDirectory.exists() || !tmpDirectory.isDirectory()) {

            logger.debug("Directory does not exist.");
            System.exit(0);

        } else {
            if (tmpDirectory.list().length == 0) {
                logger.debug("Directory is already empty : "
                        + tmpPath);
            }
            String files[] = tmpDirectory.list();
            for (String temp : files) {
                File fileDelete = new File(tmpDirectory, temp);
                fileDelete.delete();
                logger.debug("File is deleted : " + fileDelete.getAbsolutePath());
            }
        }
    }

    private Map<String, List<String>> loadFiles(String collection) throws IOException {
        return mongoManager.getFilenames(collection);
    }

    private void processGrobid(Map<String, List<String>> filenames) {

        ExecutorService executor = Executors.newFixedThreadPool(NTHREDS);
        for (String date : dates) {
            List<String> dateFilenames = filenames.get(date);
            if (dateFilenames != null) {
                for (final String filename : dateFilenames) {
                    try {
                        Runnable worker = new GrobidWorker(filename, mongoManager, grobid_host, grobid_port, date);
                        executor.execute(worker);
                    } catch (final Exception exp) {
                        logger.error("An error occured while processing the file " + filename
                                + ". Continuing the process for the other files" + exp.getMessage());
                    }
                }
            }
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
        System.out.println("Finished all threads");
    }

    private void merge(Map<String, List<String>> filenames) throws ParserConfigurationException, IOException, SAXException, TransformerException, ParseException {
        InputStream grobid_tei = null;
        InputStream hal_tei = null;
        List<InputStream> ins = null;

        for (String date : dates) {
            List<String> dateFilenames = filenames.get(date);
            if (dateFilenames != null) {
                logger.debug("Merging documents.. for: " + date);
                for (final String filename : dateFilenames) {
                    try {
                        logger.debug("Merging documents.. for: " + filename);
                        grobid_tei = mongoManager.streamFile(filename, MongoManager.GROBID_TEI_NAMESPACE);
                        hal_tei = mongoManager.streamFile(filename, MongoManager.OAI_TEI_NAMESPACE);
                        InputStream tei = new ByteArrayInputStream(addHalTeiHeader(hal_tei, grobid_tei).getBytes());

                        mongoManager.storeToGridfs(tei, filename, MongoManager.GROBID_HAL_TEI_NAMESPACE, date);
                        grobid_tei.close();
                        hal_tei.close();
                    } catch (SAXParseException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private String addHalTeiHeader(InputStream halTei, InputStream grobidTei) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String result = HalTeiAppender.replaceHeader(halTei, grobidTei, false);
        return result;
    }

    public static boolean askConfirm() {

        Scanner kbd = new Scanner(System.in);
        String decision = null;

        boolean yn = true;
        System.out.println("Are you sur you want to start harvesting all hal documents ? [yes]");
        decision = kbd.nextLine();

        switch (Decision.valueOf(decision)) {
            case yes:
                break;

            case no:
                yn = false;
                break;

            default:
                break;
        }
        return yn;
    }

    protected static String getHelp() {
        final StringBuffer help = new StringBuffer();
        help.append("HELP HAL_OAI_HARVESTER\n");
        help.append("-h: displays help\n");
        help.append("-exe: gives the command to execute. The value should be one of these : [harvestDaily | harvestAll]\n");
        return help.toString();
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
