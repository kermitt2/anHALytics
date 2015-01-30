package org.harvesthal;

import com.mongodb.MongoClient;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class MongoManager {

    public static final String OAI_NAMESPACE = "oairesponses";
    public static final String OAI_TEI_NAMESPACE = "oaiteis";
    public static final String BINARY_NAMESPACE = "binarynamespaces";
    public static final String GROBID_NAMESPACE = "halheader_grobidbody";

    public static String filePath = "";
    private List<String> filenames = null;
    public DB db = null;

    public MongoManager() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("harvestHal.properties"));
            String mongodbServer = prop.getProperty("harvestHal.mongodbServer");
            int mongodbPort = Integer.parseInt(prop.getProperty("harvestHal.mongodbPort"));
            String mongodbDb = prop.getProperty("harvestHal.mongodbDb");
            String mongodbUser = prop.getProperty("harvestHal.mongodbUser");
            String mongodbPass = prop.getProperty("harvestHal.mongodbPass");
            MongoClient mongo = new MongoClient(mongodbServer, mongodbPort);
            db = mongo.getDB(mongodbDb);
            boolean auth = db.authenticate(mongodbUser, mongodbPass.toCharArray());
            filenames = new ArrayList<String>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void storeToGridfs(InputStream file, String fileName, String namespace, String dateString) throws ParseException {

        try {
            GridFS gfs = new GridFS(db, namespace);
            gfs.remove(fileName);
            GridFSInputFile gfsFile = gfs.createFile(file, true);
            gfsFile.put("uploadDate", parseStringDate(dateString));
            gfsFile.setFilename(fileName);
            gfsFile.save();

        } catch (MongoException e) {
            e.printStackTrace();
        }
    }

    public void storeToGridfs(String filePath, String fileName, String namespace, String dateString) throws IOException, ParseException {

        try {
            GridFS gfs = new GridFS(db, namespace);
            File f = new File(filePath);
            gfs.remove(fileName);
            GridFSInputFile gfsFile = gfs.createFile(f);
            gfsFile.put("uploadDate", parseStringDate(dateString));
            gfsFile.setFilename(fileName);
            gfsFile.save();

        } catch (MongoException e) {
            e.printStackTrace();
        }
    }

    public List<String> getFilenames() throws IOException {
        try {
            GridFS gfs = new GridFS(db, BINARY_NAMESPACE);
            // print the result
            DBCursor cursor = gfs.getFileList();
            while (cursor.hasNext()) {
                filenames.add((String) cursor.next().get("filename"));
            }
            
        } catch (MongoException e) {
        }
        return filenames;
    }

    private Date parseStringDate(String dateString) throws ParseException {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Date date = format.parse(dateString);
        return date;
    }

    public InputStream getHalTei(String filename) {
        GridFSDBFile file = null;
        try {
            GridFS gfs = new GridFS(db, OAI_TEI_NAMESPACE);
            file = gfs.findOne(filename);

        } catch (MongoException e) {
        }
        return file.getInputStream();
    }

    public InputStream streamFile(String filename) {
        GridFSDBFile file = null;
        try {
            GridFS gfs = new GridFS(db, BINARY_NAMESPACE);
            file = gfs.findOne(filename);
            
        } catch (MongoException e) {
        }
        return file.getInputStream();
    }
}
