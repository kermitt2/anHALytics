package org.harvesthal;

import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.ParseException;
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
            String request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&metadataPrefix=xml-tei&set=collection:" + affiliations.get(0) + "&from=" + date + "&until=" + date;

            if (tokenn != null) {
                request = "http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&resumptionToken=" + tokenn;
            }
            String fileName = "oai-inria-" + loop + ".xml";

            System.out.println("Sending: " + request);

            InputStream in = request(request);
            ArrayList<InputStream> ins = cloneInputStream(in, 2);
            in.close();

            mongoManager.storeToGridfs(ins.get(0), fileName, MongoManager.OAI_NAMESPACE, date);
            ins.get(0).close();

            OAISaxParser oaisax = parse(ins.get(1));
            ins.get(1).close();

            //teis
            System.out.println("\t Extracting teis.... for " + date);
            Map<String, StringBuilder> teis = oaisax.getTeis();
            Map<String, String> urls = oaisax.getBinaryUrls();
            Iterator it1 = teis.entrySet().iterator();
            while (it1.hasNext()) {
                Map.Entry pairsIdTei = (Map.Entry) it1.next();
                String id = (String) pairsIdTei.getKey();
                try {
                    String teiFilename = id + ".tei.xml";
                    System.out.println("\t\t Extracting tei.... for " + id);
                    StringBuilder tei = (StringBuilder) pairsIdTei.getValue();
                    if (tei.length() > 0) {
                        String formatedTei = xmlFormatter.format(tei.toString());
                        mongoManager.storeToGridfs(new ByteArrayInputStream(formatedTei.getBytes()), teiFilename, MongoManager.OAI_TEI_NAMESPACE, date);

                        //binary processing.
                        if (urls.containsKey(id)) {
                            String binaryUrl = urls.get(id);
                            System.out.println("\t\t\t Downloading: " + binaryUrl);
                            InputStream inBinary = new BufferedInputStream(request(binaryUrl));
                            String tmpFilePath = storeTmpFile(inBinary);
                            inBinary.close();

                            System.out.println("\t\t\t Grobid processing...");
                            String grobidTei = getTeiFromBinary(tmpFilePath);
                            InputStream inTeiGrobid = new ByteArrayInputStream(grobidTei.getBytes());
                            mongoManager.storeToGridfs(inTeiGrobid, teiFilename, MongoManager.GROBID_NAMESPACE, date);
                            //mongoManager.storeToGridfs(tmpFilePath, (String) pairsIdTei.getKey() + ".pdf", MongoManager.BINARY_NAMESPACE, date);
                            inTeiGrobid.close();

                        } else {
                            System.out.println("\t\t\t PDF not found !");
                        }
                    } else {
                        System.out.println("\t\t\t Tei not found !!!");
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
            throws IOException, SAXException, ParserConfigurationException, ParseException {
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
                        if (askConfirm()) {
                            oai.harvestAllHAL();
                        } else {
                            return;
                        }
                    } else if (process.equals("harvestDaily")) {
                        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                        Date date = new Date();
                        oai.harvestHALForDate(dateFormat.format(date));
                    } else {
                        System.err.println("-exe value should be one value from [harvestDaily | harvestAll] ");
                        break;
                    }

                }
            }
        }
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
