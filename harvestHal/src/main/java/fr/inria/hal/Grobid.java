package fr.inria.hal;

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

	/**
	 *  Call the Grobid full text extraction method via the Java API.
	 *
	 *  @param pdfPath path to the PDF file to be processed
	 *  @param start first page of the PDF to be processed, default -1 first page
	 *  @param last last page of the PDF to be processed, default -1 last page	
	 *  @return the resulting TEI document as a String or null if the extraction failed	
	 */
    public String runFullTextGrobid(String pdfPath, int start, int end, boolean generateIDs) {
        String tei = null;
        try {
			tei = engine.fullTextToTEI(pdfPath, false, false, null, 1, -1, generateIDs);
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
