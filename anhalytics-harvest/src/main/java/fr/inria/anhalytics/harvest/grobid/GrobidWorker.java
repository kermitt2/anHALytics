package fr.inria.anhalytics.harvest.grobid;

import fr.inria.anhalytics.commons.managers.MongoManager;
import fr.inria.anhalytics.commons.utilities.Utilities;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Achraf
 */
public class GrobidWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GrobidWorker.class);
    private InputStream content;
    private MongoManager mm;
    private String grobid_host;
    private String grobid_port;
    private String date;
    private String filename;

    public GrobidWorker(InputStream content, MongoManager mongoManager, String grobidHost, String grobidPort, String date) {
        this.content = content;
        this.mm = mongoManager;
        this.grobid_host = grobidHost;
        this.grobid_port = grobidPort;
        this.date = date;
        this.filename = mm.getCurrentFilename();
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
            System.out.println(filename);
            GrobidService grobidService = new GrobidService(grobid_host, grobid_port, 2, -1, true, date);
            String zipPath = grobidService.runFullTextGrobid(content);
            storeToGridfs(zipPath);
            (new File(zipPath)).delete();
            logger.debug("\t\t "+filename+" for "+date+" processed.");
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }
    
    private void storeToGridfs(String zipDirectoryPath) {
        String tei = null;
        try {
            File directoryPath = new File(zipDirectoryPath);
            if (directoryPath.exists()) {
                File[] files = directoryPath.listFiles();
                if (files != null) {
                    for (final File currFile : files) {
                        if (currFile.getName().toLowerCase().endsWith(".png")) {
                            InputStream targetStream = FileUtils.openInputStream(currFile);
                            mm.addAssetDocument(targetStream, Utilities.getHalIDFromFilename(filename), currFile.getName(), MongoManager.ASSETS, date);
                            targetStream.close();
                        } else if (currFile.getName().toLowerCase().endsWith(".xml")) {
                            tei = Utilities.readFile(currFile.getAbsolutePath());
                            tei = Utilities.trimEncodedCharaters(tei);
                            System.out.println(filename);
                            mm.addDocument(new ByteArrayInputStream(tei.getBytes()), filename.substring(0, filename.indexOf("."))+".tei.xml", MongoManager.GROBID_TEIS, date);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return this.filename;
    }
}
