package fr.inria.anhalytics.harvest;

import fr.inria.anhalytics.commons.managers.MongoManager;
import fr.inria.anhalytics.commons.utilities.Utilities;
import java.io.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpRetryException;
import java.net.ConnectException;

import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.grobid.core.utilities.KeyGen;

/**
 * Call of Grobid process via its REST web services.
 *
 * @author Patrice Lopez
 */
public class GrobidService {
    
    private String grobid_host = null;
    private String grobid_port = null;
    private String filename = null;
    private int start = -1;
    private int end = -1;
    private boolean generateIDs = false;
    private MongoManager mm;
    private String date;
    
    public GrobidService(String filename, MongoManager mongoManager, String grobidHost, String grobidPort,
            int start, int end, boolean generateIDs, String date) {
        this.filename = filename;
        this.grobid_host = grobidHost;
        this.grobid_port = grobidPort;
        this.start = start;
        this.end = end;
        this.generateIDs = generateIDs;
        this.mm = mongoManager;
        this.date = date;
        
    }

    /**
     * Call the Grobid full text extraction service on server.
     *
     * @param pdfPath path to the PDF file to be processed
     * @param start first page of the PDF to be processed, default -1 first page
     * @param last last page of the PDF to be processed, default -1 last page
     * @return the resulting TEI document as a String or null if the service
     * failed
     */
    public void runFullTextGrobid() {
        String zipDirectoryPath = null;
        String tei = null;
        File zipFolder = null;
        try {
            URL url = new URL("http://" + grobid_host + ":" + grobid_port + "/processFulltextAssetDocument");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            
            InputStream inBinary = mm.nextBinaryDocument();
            String filepath = Utilities.storeTmpFile(inBinary);
            FileBody fileBody = new FileBody(new File(filepath));
            MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.STRICT);
            multipartEntity.addPart("input", fileBody);
            
            if (start != -1) {
                StringBody contentString = new StringBody("" + start);
                multipartEntity.addPart("start", contentString);
            }
            if (end != -1) {
                StringBody contentString = new StringBody("" + end);
                multipartEntity.addPart("end", contentString);
            }
            if (generateIDs) {
                StringBody contentString = new StringBody("1");
                multipartEntity.addPart("generateIDs", contentString);
            }
            
            conn.setRequestProperty("Content-Type", multipartEntity.getContentType().getValue());
            OutputStream out = conn.getOutputStream();
            try {
                multipartEntity.writeTo(out);
            } finally {
                out.close();
            }
            
            if (conn.getResponseCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                throw new HttpRetryException("Failed : HTTP error code : "
                        + conn.getResponseCode(), conn.getResponseCode());
            }

            //int status = connection.getResponseCode();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }
            
            InputStream in = conn.getInputStream();
            zipDirectoryPath = Utilities.getTmpPath() + "/" + KeyGen.getKey();
            zipFolder = new File(zipDirectoryPath);
            if (!zipFolder.exists()) {
                zipFolder.mkdir();
            }
            FileOutputStream zipStream = new FileOutputStream(zipDirectoryPath + "/" + "out.zip");
            IOUtils.copy(in, zipStream);
            zipStream.close();
            in.close();
            
            Utilities.unzipIt(zipDirectoryPath + "/" + "out.zip", zipDirectoryPath);
            storeToGridfs(zipDirectoryPath);
            
            conn.disconnect();
            inBinary.close();
        } catch (ConnectException e) {
            e.printStackTrace();
            try {
                Thread.sleep(20000);
                runFullTextGrobid();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        } catch (HttpRetryException e) {
            e.printStackTrace();
            try {
                Thread.sleep(20000);
                runFullTextGrobid();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            zipFolder.delete();
        }
    }
    
    private void storeToGridfs(String zipDirectoryPath) {
        String tei = null;
        try {
            File directoryPath = new File(zipDirectoryPath);
            if (directoryPath.exists()) {
                File[] files = directoryPath.listFiles();
                if (files != null) {
                    for (final File currFile : files) {
                        if (currFile.getName().toLowerCase().endsWith(".png")) {
                            InputStream targetStream = FileUtils.openInputStream(currFile);
                            mm.addAssetDocument(targetStream, Utilities.getHalIDFromFilename(filename), filename, MongoManager.ASSETS, date);
                            targetStream.close();
                        } else if (currFile.getName().toLowerCase().endsWith(".xml")) {
                            tei = Utilities.readFile(currFile.getAbsolutePath());
                            tei = Utilities.trimEncodedCharaters(tei);
                            mm.addDocument(new ByteArrayInputStream(tei.getBytes()), filename, MongoManager.GROBID_TEIS, date);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
