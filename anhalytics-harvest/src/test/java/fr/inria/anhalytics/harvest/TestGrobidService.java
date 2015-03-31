package fr.inria.anhalytics.harvest;


import fr.inria.anhalytics.harvest.GrobidService;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

/**
 *  @author Patrice Lopez
 */
public class TestGrobidService extends BaseTest {
	
	/*@Test
	public void testFullText() throws Exception {
            if (checkGrobidService()) {
                File pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile()
                        + "/hal-01110586.pdf");
                if (!pdfFile.exists()) {
                    throw new Exception("Cannot start test, because test resource folder is not correctly set.");
                }
                GrobidService grobidService = new GrobidService(pdfFile.getPath(), grobid_host, grobid_port, 2, -1, true);
                String fulltext = grobidService.runFullTextGrobid();
		// some test here...
                //System.out.println(fulltext);
                FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile()
                        + "/hal-01110586v1.fulltext.tei.xml"), fulltext, "UTF-8");

                pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile()
                        + "/hal-01110668.pdf");
                if (!pdfFile.exists()) {
                    throw new Exception("Cannot start test, because test resource folder is not correctly set.");
                }
                grobidService = new GrobidService(pdfFile.getPath(), grobid_host, grobid_port, 2, -1, true);
                fulltext = grobidService.runFullTextGrobid();
		// some test here...
                //System.out.println(fulltext);
                FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile()
                        + "/hal-01110668v1.fulltext.tei.xml"), fulltext, "UTF-8");

            } else {
                System.out.println("Grobig service is not available. test skipped.");
            }
		/*pdfFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/TheseBuren-Becquet.pdf");
		if (!pdfFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		submit = executor.submit(new GrobidService(pdfFile.getPath(), grobid_host, grobid_port, 2, -1, true)); 
		fulltext = submit.get();
		// some test here...
		//System.out.println(fulltext);
		FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/TheseBuren-Becquet.fulltext.tei.xml"), fulltext, "UTF-8");
                        */
	//}
	
}