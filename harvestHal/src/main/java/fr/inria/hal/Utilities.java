package fr.inria.hal;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.grobid.core.utilities.KeyGen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

/**
 *
 * @author Achraf
 */
public class Utilities {    
    
    private static final Logger logger = LoggerFactory.getLogger(Utilities.class);
    
    private static Set<String> dates = new LinkedHashSet<String>();
    private static String tmpPath;

    public static void setTmpPath(String tmp_path){
        tmpPath = tmp_path;
    }
    
    static {
        Calendar toDay = Calendar.getInstance();
        int todayYear = toDay.get(Calendar.YEAR);
        int todayMonth = toDay.get(Calendar.MONTH) + 1;
        for (int year = todayYear; year >= 1960; year--) {
            int monthYear = (year == todayYear) ? todayMonth : 12;
            for (int month = monthYear; month >= 1; month--) {
                for (int day = daysInMonth(year, month); day >= 1; day--) {
                    StringBuilder date = new StringBuilder();
                    date.append(String.format("%04d", year));
                    date.append("-");
                    date.append(String.format("%02d", month));
                    date.append("-");
                    date.append(String.format("%02d", day));
                    getDates().add(date.toString());
                }
            }
        }
    }
    
    public static void updateDates(String fromDate, String untilDate) {
        boolean isOkDate = false;
        for(String date:dates){
            if(untilDate != null){
                if(date.equals(untilDate))
                    isOkDate = true;
            }
            
            if(!isOkDate)
                dates.remove(date);
                       
            if(fromDate != null){
                if(date.equals(fromDate))
                    isOkDate = false;
            }
        }
    }
        
    private static int daysInMonth(int year, int month) {
        int daysInMonth;
        switch (month) {
            case 1:
            case 3:
            case 5:
            case 7:
            case 8:
            case 10:
            case 12:
                daysInMonth = 31;
                break;
            case 2:
                if (((year % 4 == 0) && (year % 100 != 0)) || (year % 400 == 0)) {
                    daysInMonth = 29;
                } else {
                    daysInMonth = 28;
                }
                break;
            default:
                // returns 30 even for nonexistant months 
                daysInMonth = 30;
        }
        return daysInMonth;
    }
    
    public static boolean isValidDate(String dateString) {
        return dates.contains(dateString);
    }
    
    public static String toString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error converting to String", ex);
        }
    }

    public static String innerXmlToString(Node node) {
        DOMImplementationLS lsImpl
                = (DOMImplementationLS) node.getOwnerDocument().getImplementation().getFeature("LS", "3.0");
        LSSerializer lsSerializer = lsImpl.createLSSerializer();
        lsSerializer.getDomConfig().setParameter("xml-declaration", false);
        NodeList childNodes = node.getChildNodes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < childNodes.getLength(); i++) {
            sb.append(lsSerializer.writeToString(childNodes.item(i)));
        }
        return sb.toString();
    }
    
    /**
    *  Add random xml ids on the textual nodes of the document 
    */
    public static void generateIDs(Document doc) {
        NodeList titles = doc.getElementsByTagName("title");
        NodeList abstracts = doc.getElementsByTagName("abstract");
        NodeList terms = doc.getElementsByTagName("term");
        NodeList funders = doc.getElementsByTagName("funder");
        NodeList codes = doc.getElementsByTagName("classCode");
        generateID(titles);
        generateID(abstracts);
        generateID(terms);
        generateID(funders);
        generateID(codes);
    }
	
    private static void generateID(NodeList theNodes) {
        for (int i = 0; i < theNodes.getLength(); i++) {
            Element theElement = (Element)theNodes.item(i);	
            String divID = KeyGen.getKey().substring(0,7);
            theElement.setAttribute("xml:id", "_" + divID);
        }
    }
	
    /**
     *  Remove starting and ending end-of-line in XML element text content recursively
     */
    public static void trimEOL(Node node, Document doc) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            String text = node.getNodeValue();
            if (text.replaceAll("[ \\t\\r\\n]+", "").length() != 0) {
                while (text.startsWith("\n") && text.length()>0) {
                    text = text.substring(1,text.length());
                }
                while (text.endsWith("\n") && text.length()>0) {
                    text = text.substring(0,text.length()-1);
                }
                node.setNodeValue(text);
            }
        }
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            //if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                trimEOL(currentNode, doc);
                    //}
        }
    }
    
    private static void clearTmpDirectory() throws IOException {
        File tmpDirectory = new File(tmpPath);
        if (!tmpDirectory.exists() || !tmpDirectory.isDirectory()) {
            logger.debug("Directory does not exist.");
            System.exit(0);
        } else {
            if (tmpDirectory.list().length == 0) {
                logger.debug("Directory is already empty : "
                        + tmpPath);
            }
            String files[] = tmpDirectory.list();
            for (String temp : files) {
                File fileDelete = new File(tmpDirectory, temp);
                fileDelete.delete();
                logger.debug("File is deleted : " + fileDelete.getAbsolutePath());
            }
        }
    }
    
    public static String storeTmpFile(InputStream inBinary) throws IOException {
        File f = File.createTempFile("tmp", ".pdf", new File(tmpPath));
        // deletes file when the virtual machine terminate
        f.deleteOnExit();
        String filePath = f.getAbsolutePath();
        getBinaryURLContent(f, inBinary);
        return filePath;
    }

    public static String storeToTmpXmlFile(InputStream inBinary) throws IOException {
        File f = File.createTempFile("tmp", ".xml", new File(tmpPath));
        // deletes file when the virtual machine terminate
        f.deleteOnExit();
        String filePath = f.getAbsolutePath();
        getBinaryURLContent(f, inBinary);
        return filePath;
    }
    
     /**
     * Download binaries from a given URL
     */
    public static void getBinaryURLContent(File file, InputStream in) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        DataOutputStream writer = new DataOutputStream(fos);
        try {
            byte[] buf = new byte[4 * 1024]; // 4K buffer
            int bytesRead;
            while ((bytesRead = in.read(buf)) != -1) {
                writer.write(buf, 0, bytesRead);
            }
        } finally {
            in.close();
        }
    }

    /**
     * @return the dates
     */
    public static Set<String> getDates() {
        return dates;
    }
}
