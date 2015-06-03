package fr.inria.anhalytics.commons.utilities;

import fr.inria.anhalytics.commons.exceptions.BinaryNotAvailableException;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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

    public static void setTmpPath(String tmp_path) {
        tmpPath = tmp_path;
    }

    public static String getTmpPath() {
        return tmpPath;
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
        boolean isOkDate = true;
        if (untilDate != null) {
            isOkDate = false;
        }
        String[] dates1 = new String[dates.size()];
        dates.toArray(dates1);
        for (String date : dates1) {
            if (date.equals(untilDate)) {
                isOkDate = true;
            }
            if (!isOkDate) {
                dates.remove(date);
            }
            if (fromDate != null) {
                if (date.equals(fromDate)) {
                    isOkDate = false;
                }
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
    
    public static Document removeElement(Document doc, String elementTagName) {
        Element element = (Element) doc.getElementsByTagName(elementTagName).item(0);
        element.getParentNode().removeChild(element);
        doc.normalize();
        return doc;
    }

    public static Node findNode(String id, NodeList orgs) {
        Node org = null;
        for (int i = 0; i < orgs.getLength(); i++) {
            NamedNodeMap attr = orgs.item(i).getAttributes();
            if (attr.getNamedItem("xml:id") == null) {
                continue;
            }
            if (attr.getNamedItem("xml:id").getNodeValue().equals(id)) {
                org = orgs.item(i);
                break;
            }
        }
        return org;
    }

    /**
     * Add random xml ids on the textual nodes of the document
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
            Element theElement = (Element) theNodes.item(i);
            String divID = KeyGen.getKey().substring(0, 7);
            theElement.setAttribute("xml:id", "_" + divID);
        }
    }

    /**
     * Remove starting and ending end-of-line in XML element text content
     * recursively
     */
    public static void trimEOL(Node node, Document doc) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            String text = node.getNodeValue();
            if (text.replaceAll("[ \\t\\r\\n]+", "").length() != 0) {
                while (text.startsWith("\n") && text.length() > 0) {
                    text = text.substring(1, text.length());
                }
                while (text.endsWith("\n") && text.length() > 0) {
                    text = text.substring(0, text.length() - 1);
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

    public static void clearTmpDirectory() {
        try {
            File tmpDirectory = new File(tmpPath);
            FileUtils.cleanDirectory(tmpDirectory);
            logger.debug("Temporary directory is cleaned.");
        } catch (Exception exp) {
            logger.error("Error while deleting the temporary directory: " + exp);
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

    public static String formatDate(Date date) {
        SimpleDateFormat dt1 = new SimpleDateFormat("yyyy-MM-dd");
        return dt1.format(date);
    }

    public static Date parseStringDate(String dateString) throws ParseException {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Date date = format.parse(dateString);
        return date;
    }

    public static String getHalIDFromFilename(String filename) {
        int ind = filename.indexOf(".");
        String halID = filename.substring(0, ind);
        ind = halID.lastIndexOf("v");
        if(ind > -1)
            halID = halID.substring(0, ind);
        return halID;
    }

    /**
     * @return the dates
     */
    public static Set<String> getDates() {
        return dates;
    }

    public static String trimEncodedCharaters(String string) {
        return string.replaceAll("&amp\\s+;", "&amp;").
                replaceAll("&quot[^;]|&amp;quot\\s*;", "&quot;").
                replaceAll("&lt[^;]|&amp;lt\\s*;", "&lt;").
                replaceAll("&gt[^;]|&amp;gt\\s*;", "&gt;").
                replaceAll("&apos[^;]|&amp;apos\\s*;", "&apos;");
    }

    public static void unzipIt(String file, String outPath) {
        try {
            ZipInputStream zis
                    = new ZipInputStream(new FileInputStream(new File(file)));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();
            byte[] buffer = new byte[1024];
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(outPath + "/" + fileName);
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();
        } catch (Exception e) {

        }
    }

    public static String readFile(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }
    
    public static InputStream request(String request, boolean retry) {
        InputStream in = null;
        try {
            URL url = new URL(request);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("accept-charset", "UTF-8");
            in = conn.getInputStream();
            return in;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            if(retry) {
                try {
                    Thread.sleep(900000); //take a nap.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }            
                in = request(request, true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return in;
    }
}
