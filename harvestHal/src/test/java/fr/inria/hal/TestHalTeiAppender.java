package fr.inria.hal;


import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.util.Iterator;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.xml.sax.InputSource;

/**
 *  @author Patrice Lopez
 */
public class TestHalTeiAppender extends BaseTest {

	@Test
	public void testTEIMerging() throws Exception {
                XPath xPath = XPathFactory.newInstance().newXPath();
                xPath.setNamespaceContext(new MyNamespaceContext());
		File halTeiFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586v1.tei.xml");
		if (!halTeiFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}		
		File fullTextFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586v1.fulltext.tei.xml");
		if (!fullTextFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}		
		String result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), new FileInputStream(fullTextFile), false);
		//System.out.println(result);
		// some test here...
                String expected = FileUtils.readFileToString(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586v1.final.tei.xml"), "UTF-8");
                String titleHalTei = (String)xPath.evaluate("/TEI/text/body/listBibl/biblFull/titleStmt/title/text()", new InputSource(new FileInputStream(halTeiFile)), XPathConstants.STRING);
                String titleResult = (String)xPath.evaluate("/tei:TEI/tei:teiHeader/tei:titleStmt/tei:title/text()", new InputSource(new StringReader(result)), XPathConstants.STRING);
                assertEquals(titleResult.trim(),titleHalTei.trim());
                
		halTeiFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
					"/hal-01110668v1.tei.xml");
		if (!halTeiFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		fullTextFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.fulltext.tei.xml");
		if (!fullTextFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), new FileInputStream(fullTextFile), false);
		//System.out.println(result);
		expected = FileUtils.readFileToString(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.final.tei.xml"), "UTF-8");
		//assertXpathExists("/TEI[1]/teiHeader[1]", expected);
                                
                titleResult = (String)xPath.evaluate("/TEI/text/body/listBibl/biblFull/titleStmt/title/text()", new InputSource(new FileInputStream(halTeiFile)), XPathConstants.STRING);
                titleHalTei = (String)xPath.evaluate("/tei:TEI/tei:teiHeader/tei:titleStmt/tei:title/text()", new InputSource(new StringReader(result)), XPathConstants.STRING);
                assertEquals(titleResult.trim(), titleHalTei.trim());
                       
	}
	
	@Test
	public void testTEIMergingBrutal() throws Exception {
                XPath xPath = XPathFactory.newInstance().newXPath();
                xPath.setNamespaceContext(new MyNamespaceContext());
		File halTeiFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586v1.tei.xml");
		if (!halTeiFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}		
		File fullTextFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110586v1.fulltext.tei.xml");
		if (!fullTextFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}		
		String result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), new FileInputStream(fullTextFile), true);
		//System.out.println(result);                
		//FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
		//	"/hal-01110586v1.final.tei.xml"), result, "UTF-8");
		// some test here...		
                String titleHalTei = (String)xPath.evaluate("/TEI/text/body/listBibl/biblFull/titleStmt/title/text()", new InputSource(new FileInputStream(halTeiFile)), XPathConstants.STRING);
                String titleResult = (String)xPath.evaluate("/tei:TEI/tei:teiHeader/tei:titleStmt/tei:title/text()", new InputSource(new StringReader(result)), XPathConstants.STRING);
                assertEquals(titleResult.trim(),titleHalTei.trim());
                
		halTeiFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
					"/hal-01110668v1.tei.xml");
		if (!halTeiFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		fullTextFile = new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.fulltext.tei.xml");
		if (!fullTextFile.exists()) {
			throw new Exception("Cannot start test, because test resource folder is not correctly set.");
		}
		result = HalTeiAppender.replaceHeader(new FileInputStream(halTeiFile), new FileInputStream(fullTextFile), true);
		//System.out.println(result);
		FileUtils.writeStringToFile(new File(this.getResourceDir("src/test/resources/").getAbsoluteFile() + 
			"/hal-01110668v1.final.tei.xml"), result, "UTF-8");
		// some test here...
                titleHalTei = (String)xPath.evaluate("/TEI/text/body/listBibl/biblFull/titleStmt/title/text()", new InputSource(new FileInputStream(halTeiFile)), XPathConstants.STRING);
                titleResult = (String)xPath.evaluate("/tei:TEI/tei:teiHeader/tei:titleStmt/tei:title/text()", new InputSource(new StringReader(result)), XPathConstants.STRING);
                assertEquals(titleResult.trim(),titleHalTei.trim());
	}

    private static class MyNamespaceContext implements NamespaceContext {

        public String getNamespaceURI(String prefix) {
            if ("tei".equals(prefix)) {
                return "http://www.tei-c.org/ns/1.0";
            }
            return null;
        }

        public String getPrefix(String namespaceURI) {
            return null;
        }

        public Iterator getPrefixes(String namespaceURI) {
            return null;
        }

    }
}