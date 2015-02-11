package fr.inria.hal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Achraf
 */
public class GrobidWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GrobidWorker.class);
    private String filename;
    private MongoManager mm;
    private String grobid_host;
    private String grobid_port;
    private String date;

    public GrobidWorker(String filename, MongoManager mongoManager, String grobidHost, String grobidPort, String date) {
        this.filename = filename;
        this.mm = mongoManager;
        this.grobid_host = grobidHost;
        this.grobid_port = grobidPort;
        this.date = date;
    }

    @Override
    public void run() {
        try {
            long startTime = System.nanoTime();
            System.out.println(Thread.currentThread().getName() + " Start. Processing = " + filename);
            processCommand();
            long endTime = System.nanoTime();
            System.out.println(Thread.currentThread().getName() + " End. :" + (endTime - startTime) / 1000000 + " ms");
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(GrobidWorker.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseException ex) {
            java.util.logging.Logger.getLogger(GrobidWorker.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void processCommand() throws IOException, ParseException {
        try {
            String teiFilename = filename.split("\\.")[0] + ".tei.xml";
            InputStream inBinary = mm.streamFile(filename);
            String filepath = Utilities.storeTmpFile(inBinary);
            inBinary.close();
            System.out.println(filename);
            GrobidService grobidService = new GrobidService(filepath, grobid_host, grobid_port, 2, -1, true);
            InputStream inTeiGrobid = new ByteArrayInputStream(grobidService.runFullTextGrobid().getBytes());
            mm.storeToGridfs(inTeiGrobid, teiFilename, MongoManager.GROBID_TEI_NAMESPACE, date);
            inTeiGrobid.close();
            logger.debug("\t\t "+filename+" for "+date+" processed.");
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return this.filename;
    }
}
