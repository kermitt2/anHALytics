package fr.inria.anhalytics.harvest.teibuild;

import fr.inria.anhalytics.commons.managers.MongoManager;
import fr.inria.anhalytics.commons.utilities.Utilities;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Achraf
 */
public class TeiBuilderProcess {

    private static final Logger logger = LoggerFactory.getLogger(TeiBuilderProcess.class);

    private MongoManager mm;

    public TeiBuilderProcess(MongoManager mm) {
        this.mm = mm;
    }

    public void build() throws ParserConfigurationException, IOException {
        InputStream grobid_tei = null;
        InputStream additional_tei = null;
        String result;
        for (String date : Utilities.getDates()) {
            if (mm.init(MongoManager.GROBID_TEIS, date)) {
                logger.debug("Merging documents.. for: " + date);
                while (mm.hasMoreDocuments()) {
                    String filename = mm.getCurrentFilename();
                    logger.debug("\t\t Merging documents.. for: " + filename);
                    grobid_tei = new ByteArrayInputStream(mm.nextDocument().getBytes());
                    additional_tei = mm.streamFile(filename, MongoManager.ADDITIONAL_TEIS);
                    result = TeiBuilder.generateTeiCorpus(additional_tei, grobid_tei, false);
                    InputStream tei = new ByteArrayInputStream(result.getBytes());
                    mm.addDocument(tei, filename, MongoManager.FINAL_TEIS, date);
                    grobid_tei.close();
                    additional_tei.close();
                }
            }
        }
    }
}
