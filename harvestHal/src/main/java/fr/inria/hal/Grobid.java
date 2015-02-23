package fr.inria.hal;

import java.io.ByteArrayInputStream;
import java.io.File;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.mock.MockContext;
import org.grobid.core.utilities.GrobidProperties;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;
import org.grobid.core.utilities.KeyGen;

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
     * Call the Grobid full text extraction method via the Java API.
     *
     * @param pdfPath path to the PDF file to be processed
     * @param start first page of the PDF to be processed, default -1 first page
     * @param last last page of the PDF to be processed, default -1 last page
     * @return the resulting TEI document as a String or null if the extraction
     * failed
     */
    public void runFullTextGrobid(MongoManager mm, String halID, String pdfPath, int start, int end, boolean generateIDs, String date) {
        String tei = null;
        String assetPath = null;
        String fulltextTeiFilename = halID+".fulltext.tei.xml";
        try {
            assetPath = Utilities.getTmpPath() + "/" + KeyGen.getKey();
            System.out.println(assetPath);
            tei = engine.fullTextToTEI(pdfPath, false, false, assetPath, 1, -1, generateIDs);
            // no consolidation (it would take too much time)
            // startPage is 1 to skip the cover page
            // endPage is -1, meaning end of the document
            tei = Utilities.trimEncodedCaraters(tei);
            mm.storeToGridfs(new ByteArrayInputStream(tei.getBytes()), fulltextTeiFilename, MongoManager.GROBID_TEI_NAMESPACE, date);
            
            // put now the assets, i.e. all the files under the asset path
            File assetPathDir = new File(assetPath);
            if (assetPathDir.exists()) {
                File[] files = assetPathDir.listFiles();
                if (files != null) {
                    for (final File currFile : files) {
                        if (currFile.getName().toLowerCase().endsWith(".jpg")
                                || currFile.getName().toLowerCase().endsWith(".png")) {
                            try {
                                FileInputStream in = new FileInputStream(currFile);                             
                                mm.storeAssetToGridfs(in, Utilities.getHalIDFromFilename(fulltextTeiFilename), currFile.getName(), MongoManager.ASSETS_NAMESPACE, date);
                                in.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If an exception is generated, print a stack trace
            e.printStackTrace();
        } finally {
            Utilities.clearTmpDirectory();
        }
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
