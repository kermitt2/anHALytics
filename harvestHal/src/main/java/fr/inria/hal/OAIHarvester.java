package fr.inria.hal;

import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.xml.parsers.*;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.*;

public class OAIHarvester {

    private static final Logger logger = LoggerFactory.getLogger(OAIHarvester.class);

    private static final int NTHREDS = 4;
    
    private static List<String> availableCommands = new ArrayList<String>() {{
                                                                                add("harvestAll");
                                                                                add("harvestDaily");
                                                                                add("processGrobid");
                                                                                add("merge");
                                                                            }};
    private static Set<String> dates = new LinkedHashSet<String>();
    /**
    * Arguments of processes.
    */
    private static HarvesterArgs hrtArgs ;

    private ArrayList<String> fields = null;
    private ArrayList<String> affiliations = null;

    private final MongoManager mongoManager;
    private final OAIPMHDom oaiDom;
    private Grobid grobidProcess = null;
    private boolean isGrobidProcessEnabled;

    
    private static int nullBinaries = 0;

    public enum Decision {

        yes, no
    }

    public OAIHarvester() {
        
        dates = Utilities.getDates();
        mongoManager = new MongoManager();
        oaiDom = new OAIPMHDom();
        hrtArgs = new HarvesterArgs();

        fields = new ArrayList<String>();
        affiliations = new ArrayList<String>();

        affiliations.add("INRIA");

        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("harvestHal.properties"));
            hrtArgs.setGrobidHost(prop.getProperty("harvestHal.grobid_host"));
            hrtArgs.setGrobidPort(prop.getProperty("harvestHal.grobid_port"));
            hrtArgs.setTmpPath(prop.getProperty("harvestHal.tmpPath"));
            Utilities.setTmpPath(prop.getProperty("harvestHal.tmpPath"));   
            hrtArgs.setPath2grobidHome(prop.getProperty("harvestHal.pGrobidHome"));
            hrtArgs.setPath2grobidProperty(prop.getProperty("harvestHal.pGrobidProperties"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    public void harvestHALForDate(String url, String date) throws IOException, SAXException, ParserConfigurationException, ParseException {
        boolean stop = false;
        String tokenn = null;
        InputStream inBinary = null;
        while (!stop) {
            String request = url+"/?verb=ListRecords&metadataPrefix=xml-tei&from=" + date + "&until=" + date;

            if (tokenn != null) {
                request = url+"/?verb=ListRecords&resumptionToken=" + tokenn;
            }

            logger.debug("Sending: " + request);

            InputStream in = request(request);
            logger.debug("\t Extracting teis.... for " + date);
            List<TEI> teis = oaiDom.getTeis(in);
            for(TEI tei:teis) {
                try {
                    String teiFilename = tei.getId() + ".tei.xml";
                    logger.debug("\t\t Extracting tei.... for " + tei.getId());
                    String teiString = tei.getTei();
                    if (teiString.length() > 0) {
                        logger.debug("\t\t\t\t Storing tei : " + tei.getId());
                        mongoManager.storeToGridfs(tei, teiFilename, MongoManager.OAI_TEI_NAMESPACE, date);

                        //binary processing.
                        if (tei.getFileUrl() != null) {
                            String binaryUrl = tei.getFileUrl();
                            logger.debug("\t\t\t Downloading: " + binaryUrl);
                            inBinary = new BufferedInputStream(request(binaryUrl));
                            String tmpFilePath = Utilities.storeTmpFile(inBinary);
                            System.out.println(tei.getId() + ".pdf");
                            mongoManager.storeToGridfs(tmpFilePath, tei.getId() + ".pdf", MongoManager.BINARY_NAMESPACE, date);
                            
                            if(isGrobidProcessEnabled){
                                String fulltextTeiFilename = tei.getId() + ".fulltext.tei.xml";
                                logger.debug("\t\t\t Grobid processing...");
                                String grobidTei = grobidProcess.runFullTextGrobid(tmpFilePath, 2, -1, true);
                                System.out.println(grobidTei);
                                mongoManager.storeToGridfs(new ByteArrayInputStream(grobidTei.getBytes()), fulltextTeiFilename, MongoManager.GROBID_TEI_NAMESPACE, date);                                
                            }                           
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
            tokenn = oaiDom.getToken();
            if (tokenn == null) {
                stop = true;
            }
        }
    }

    /**
     * Harvesting of all HAL repository
     */
    public void harvestAllHAL() throws IOException, SAXException, ParserConfigurationException, ParseException {
        String url = hrtArgs.getOaiUrl();
        for (String date : dates) {
            harvestHALForDate(url, date);
        }
    }

    public static void main(String[] args)
            throws IOException, SAXException, ParserConfigurationException, ParseException, TransformerException {
        OAIHarvester oai = new OAIHarvester();
        if(processArgs(args)){
            if(hrtArgs.getFromDate() != null || hrtArgs.getUntilDate() != null)
                Utilities.updateDates(hrtArgs.getFromDate(), hrtArgs.getUntilDate());
            oai.processCommand();
        }        
    }
    
    private void processCommand() throws IOException, SAXException, ParserConfigurationException, ParseException, TransformerException {
        String process = hrtArgs.getProcessName();
        if (process.equals("harvestAll")) {
            if(dates.size() > 5)
               if(askConfirm()){
                   harvestAllHAL();
                   processGrobid();
                   return;
                } else {
                    activateGrobid();
                    setGrobidProcess(new Grobid());
                    harvestAllHAL();
               }
        } else if (process.equals("harvestDaily")) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -1);
            activateGrobid();
            setGrobidProcess(new Grobid());
            harvestHALForDate(hrtArgs.getOaiUrl(), dateFormat.format(cal.getTime()));
        } else if (process.equals("processGrobid")) {
            //clearTmpDirectory();‡            
            processGrobid();
        } else if (process.equals("merge")) {
            merge();
        }
    }

    private void processGrobid() throws IOException {
        Map<String, List<String>> filenames = mongoManager.getFilenames(MongoManager.BINARY_NAMESPACE);
        ExecutorService executor = Executors.newFixedThreadPool(NTHREDS);
        for (String date : dates) {
            List<String> dateFilenames = filenames.get(date);
            if (dateFilenames != null) {
                for (final String filename : dateFilenames) {
                    try {
                        Runnable worker = new GrobidWorker(filename, mongoManager, hrtArgs.getGrobidHost(), hrtArgs.getGrobidPort(), date);
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

    private void merge() throws ParserConfigurationException, IOException, SAXException, TransformerException, ParseException {        
        Map<String, List<String>> filenames = mongoManager.getFilenames(MongoManager.GROBID_TEI_NAMESPACE);
        InputStream grobid_tei = null;
        InputStream hal_tei = null;
        String result;
        for (String date : dates) {
            List<String> dateFilenames = filenames.get(date);
            if (dateFilenames != null) {
                logger.debug("Merging documents.. for: " + date);
                for (final String filename : dateFilenames) {
                    try {
                        logger.debug("\t\t Merging documents.. for: " + filename);
                        grobid_tei = mongoManager.streamFile(filename, MongoManager.GROBID_TEI_NAMESPACE);
                        hal_tei = mongoManager.streamFile(filename, MongoManager.OAI_TEI_NAMESPACE);
                        result = HalTeiAppender.replaceHeader(hal_tei, grobid_tei, false);
                        InputStream tei = new ByteArrayInputStream(result.getBytes());
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
    
    protected static boolean processArgs(final String[] pArgs) {
        boolean result = true;
        hrtArgs.setOaiUrl("http://api.archives-ouvertes.fr/oai/hal");
        if (pArgs.length == 0) {
            System.out.println(getHelp());
        } else {
            String currArg;
            for (int i = 0; i < pArgs.length; i++) {
                currArg = pArgs[i];
                if (currArg.equals("-h")) {
                    System.out.println(getHelp());
                    result = false;
                    break;
                }                
                if (currArg.equals("-gH")) {
                    hrtArgs.setPath2grobidHome(pArgs[i + 1]);
                    if (pArgs[i + 1] != null) {
                        hrtArgs.setPath2grobidProperty((pArgs[i + 1]) + File.separator + "config" + File.separator + "grobid.properties");
                    }
                    i++;
                    continue;
	        }
                if (currArg.equals("-dOAI")) {
                    hrtArgs.setOaiUrl(pArgs[i + 1]);
                    i++;
                    continue;
                }
                if (currArg.equals("-dFromDate")) {
                    String stringDate = pArgs[i + 1];
                    if(!stringDate.isEmpty()){
                        if(Utilities.isValidDate(stringDate)){
                            hrtArgs.setFromDate(pArgs[i + 1]);
                        } else {
                            System.err.println("The date given is not correct, make sure it follows the pattern : yyyy-MM-dd");
                            result = false;
                        }
                    }
                    i++;
                    continue;
		}
                if (currArg.equals("-dUntilDate")) {
                    String stringDate = pArgs[i + 1];
                    if(!stringDate.isEmpty()){
                        if(Utilities.isValidDate(stringDate)){
                            hrtArgs.setUntilDate(stringDate);
                        } else {
                            System.err.println("The date given is not correct, make sure it follows the pattern : yyyy-MM-dd");
                            result = false;
                        }
                    }
                    i++;
                    continue;
		}
                if (currArg.equals("-exe")) {
                    String command = pArgs[i + 1];
                    if (availableCommands.contains(command)) {
                        hrtArgs.setProcessName(command);
                        i++;
                        continue;
                    } else {
                        System.err.println("-exe value should be one value from this list: " + availableCommands);
                        result = false;
                        break;
                    }
            }
            }
        }     
        return result;
    }
    
    public static boolean askConfirm() {
        Scanner kbd = new Scanner(System.in);
        String decision = null;
        boolean yn = true;
        System.out.println("You are about to process a huge number of documents, using multithreaded grobid process is recommended, continue? [yes]");
        decision = kbd.nextLine();
        try{
            switch (Decision.valueOf(decision)) {
                case yes:
                    break;

                case no:
                    yn = false;
                    break;

                default:
                    break;
            }
        }catch(IllegalArgumentException ex){
            //yes by default 
        }
        return yn;
    }

    protected static String getHelp() {
        final StringBuffer help = new StringBuffer();
        help.append("HELP HAL_OAI_HARVESTER\n");
        help.append("-h: displays help\n");
        help.append("-exe: gives the command to execute. The value should be one of these : \n");
        help.append("\t" + availableCommands + "\n");
        return help.toString();
    }
    
    public static InputStream request(String request) {
        InputStream in = null;
        try {
            URL url = new URL(request);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("accept-charset", "UTF-8");
            if (request.endsWith("document") && !conn.getContentType().equals("application/pdf")) {
                nullBinaries++;
                logger.debug("\t\t\t Cannot download pdf file, because input file is null.");
                throw new BinaryNotAvailableException("Cannot download pdf file, because input file is null.");
            }
            in = conn.getInputStream();
            return in;

        } catch(UnknownHostException e){
            e.printStackTrace();
            try {
                Thread.sleep(1800000); //take a nap.
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return request(request);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return in;
    }
    
    public void setGrobidProcess(Grobid grobidProcess) {
        this.grobidProcess = grobidProcess;
    }

    private void activateGrobid() {
        this.isGrobidProcessEnabled = true;
    }
}