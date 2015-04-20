package fr.inria.anhalytics.harvest.merge;

import fr.inria.anhalytics.commons.managers.MongoManager;
import fr.inria.anhalytics.commons.utilities.Utilities;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author Achraf
 */
public class MergeProcess {

    private static final Logger logger = LoggerFactory.getLogger(MergeProcess.class);

    private MongoManager mm;

    public MergeProcess(MongoManager mm) {
        this.mm = mm;
    }

    public void merge() throws ParserConfigurationException, IOException {
        InputStream grobid_tei = null;
        InputStream hal_tei = null;
        String result;
        for (String date : Utilities.getDates()) {
            if (mm.init(MongoManager.GROBID_TEIS, date)) {
                logger.debug("Merging documents.. for: " + date);
                while (mm.hasMoreDocuments()) {
                    String filename = mm.getCurrentFilename();
                    logger.debug("\t\t Merging documents.. for: " + filename);
                    grobid_tei = new ByteArrayInputStream(mm.nextDocument().getBytes());
                    hal_tei = mm.streamFile(filename, MongoManager.HAL_TEIS);
                    result = HalTeiAppender.replaceHeader(hal_tei, grobid_tei, false);
                    InputStream tei = new ByteArrayInputStream(result.getBytes());
                    mm.addDocument(tei, filename, MongoManager.HALHEADER_GROBIDBODY_TEIS, date);
                    grobid_tei.close();
                    hal_tei.close();
                }
            }
        }
    }
}
