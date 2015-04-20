package fr.inria.anhalytics.harvest.grobid;

import fr.inria.anhalytics.commons.managers.MongoManager;
import java.io.File;
import java.io.InputStream;

/**
 *
 * @author Achraf
 */
public class GrobidAnnexWorker extends GrobidWorker {

    public GrobidAnnexWorker(InputStream content, MongoManager mongoManager, String grobidHost, String grobidPort, String date) {
        super(content, mongoManager, grobidHost, grobidPort, date);
    }

    protected void storeToGridfs(String zipDirectoryPath) {
        String tei = null;
        try {
            File directoryPath = new File(zipDirectoryPath);
            if (directoryPath.exists()) {
                File[] files = directoryPath.listFiles();
                if (files != null) {
                    for (final File currFile : files) {

                        if (currFile.getName().toLowerCase().endsWith(".png")) {
                        } else if (currFile.getName().toLowerCase().endsWith(".xml")) {
                        }

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
