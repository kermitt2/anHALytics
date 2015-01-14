package org.harvesthal;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

public class MongoManager {

    public static final String OAI_NAMESPACE = "oairesponses";
    public static final String OAI_TEI_NAMESPACE = "oaiteis";
    public static final String BINARY_NAMESPACE = "binarynamespaces";
    public static final String GROBID_NAMESPACE = "teigrobidnamespaces";

    public static String filePath = "";
    public String mongodbServer = "";
    public int mongodbPort;
    public String mongodbDb = "";
    public DB db = null;

    public MongoManager() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("harvestHal.properties"));
            mongodbServer = prop.getProperty("harvestHal.mongodbServer");
            mongodbPort = Integer.parseInt(prop.getProperty("harvestHal.mongodbPort"));
            mongodbDb = prop.getProperty("harvestHal.mongodbDb");
            Mongo mongo = new Mongo(mongodbServer, mongodbPort);
            db = mongo.getDB(mongodbDb);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void storeToGridfs(InputStream file, String fileName, String namespace, String dateString) throws ParseException {

        try {
            GridFS gfs = new GridFS(db, namespace);
            GridFSInputFile gfsFile = gfs.createFile(file, true);
            gfsFile.put("uploadDate", parseStringDate(dateString));
            gfsFile.setFilename(fileName);
            gfsFile.save();

        } catch (MongoException e) {
        }
    }

    public void storeToGridfs(String filePath, String fileName, String namespace, String dateString) throws IOException, ParseException {

        try {
            GridFS gfs = new GridFS(db, namespace);
            File f = new File(filePath);
            GridFSInputFile gfsFile = gfs.createFile(f);
            gfsFile.put("uploadDate", parseStringDate(dateString));
            gfsFile.setFilename(fileName);
            gfsFile.save();

        } catch (MongoException e) {
        }
    }

    private Date parseStringDate(String dateString) throws ParseException {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Date date = format.parse(dateString);
        return date;
    }
}
