package org.harvesthal;

import org.grobid.core.data.BiblioItem;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.mock.MockContext;
import org.grobid.core.utilities.GrobidProperties;
import java.util.Properties;
import java.io.FileInputStream;

public class Grobid {

    private static Engine engine;

    public Grobid() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("harvestHal.properties"));
            String pGrobidHome = prop.getProperty("harvestHal.pGrobidHome");
            String pGrobidProperties = prop.getProperty("harvestHal.pGrobidProperties");

            MockContext.setInitialContext(pGrobidHome, pGrobidProperties);
            GrobidProperties.getInstance();

            engine = GrobidFactory.getInstance().createEngine();
        } catch (Exception e) {
            // If an exception is generated, print a stack trace
            e.printStackTrace();
        } finally {
            try {
                MockContext.destroyInitialContext();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String runFullTextGrobid(String pdfPath) {
        String tei = null;
        try {
			tei = engine.fullTextToTEI(pdfPath, false, false, null, 1, -1);
			// no consolidation (it would take too much time)
			// document assets not saved
			// startPage is 1 to skip the cover page
			// endPage is -1, meaning end of the document 
        } catch (Exception e) {
            // If an exception is generated, print a stack trace
            e.printStackTrace();
        }
        return tei;
    }

    public String runHeaderGrobid(String pdfPath) {
	String tei = null;
        try {
	    BiblioItem resHeader = new BiblioItem();
            tei = engine.processHeader(pdfPath, false, resHeader);
        } catch (Exception e) {
            // If an exception is generated, print a stack trace
            e.printStackTrace();
        }
        return tei;
    }

}
