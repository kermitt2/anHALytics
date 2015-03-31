package fr.inria.anhalytics.harvest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import org.custommonkey.xmlunit.XMLTestCase;
import org.junit.Before;

/**
 * @author Patrice Lopez
 */
abstract class BaseTest extends XMLTestCase {

    protected String grobid_host = null;
    protected String grobid_port = null;

    protected int nbThreads = 1;

    public File getResourceDir(String resourceDir) throws Exception {
        File file = new File(resourceDir);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw new Exception("Cannot start test, because test resource folder is not correctly set.");
            }
        }
        return (file);
    }

    @Before
    protected void setUp() throws Exception {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("harvestHal.properties"));
            grobid_host = prop.getProperty("harvestHal.grobid_host");
            grobid_port = prop.getProperty("harvestHal.grobid_port");
        } catch (Exception e) {
            System.err.println("Failed to load properties: " + e.getMessage());
            e.printStackTrace();
        }
    }

    protected boolean checkGrobidService() throws IOException {
        try {
            URL url = new URL("http://" + grobid_host + ":" + grobid_port);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn.getResponseCode() == 200) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }
}
