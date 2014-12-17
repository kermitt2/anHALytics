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

    //synchronized to avoid : javax.naming.NameAlreadyBoundException: Name java: is already bound in this Context !
    synchronized public static String runGrobid(String pdfPath) {
        String tei = null;
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("harvestHal.properties"));
            String pGrobidHome = prop.getProperty("harvestHal.pGrobidHome");
            String pGrobidProperties = prop.getProperty("harvestHal.pGrobidProperties");

            MockContext.setInitialContext(pGrobidHome, pGrobidProperties);		
            GrobidProperties.getInstance();

            engine = GrobidFactory.getInstance().createEngine();

            tei = engine.fullTextToTEI(pdfPath, false, false);
        } 
        catch (Exception e) {
            // If an exception is generated, print a stack trace
            e.printStackTrace();
        } 
        finally {
            try {
                    MockContext.destroyInitialContext();
            } 
            catch (Exception e) {
                    e.printStackTrace();
            }
        }
        return tei;
    }
	
}