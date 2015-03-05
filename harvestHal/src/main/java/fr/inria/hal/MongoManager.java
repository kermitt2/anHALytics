package fr.inria.hal;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class MongoManager {

    public static final String DIAGNOSTICS = "diagnostics";
    public static final String OAI_NAMESPACE = "oairesponses";
    public static final String OAI_TEI_NAMESPACE = "oaiteis";
    public static final String BINARY_NAMESPACE = "binarynamespaces";
    public static final String GROBID_HAL_TEI_NAMESPACE = "halheader_grobidbody";
    public static final String GROBID_TEI_NAMESPACE = "grobidtei";
    public static final String ASSETS_NAMESPACE = "assets";

    public static String filePath = "";
    private Map<String, List<String>> filenames = null;
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
            filenames = new HashMap<String, List<String>>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void storeToGridfs(TEI tei, String fileName, String namespace, String dateString) throws IOException, ParseException {

        try {
            GridFS gfs = new GridFS(db, namespace);
            gfs.remove(fileName);
            GridFSInputFile gfsFile = gfs.createFile(new ByteArrayInputStream(tei.getTei().getBytes()), true);
            gfsFile.put("uploadDate", Utilities.parseStringDate(dateString));
            gfsFile.put("type", tei.getDocumentType());
            gfsFile.put("doi", tei.getDoi());
            gfsFile.put("ref", tei.getRef());
            ///gfsFile.put("subjects", tei.getSubjects());
            gfsFile.setFilename(fileName);
            gfsFile.put("halId", Utilities.getHalIDFromFilename(fileName));
            gfsFile.save();

        } catch (MongoException e) {
            e.printStackTrace();
        }
    }

    public void storeToGridfs(InputStream file, String fileName, String namespace, String dateString) throws ParseException {

        try {
            GridFS gfs = new GridFS(db, namespace);
            gfs.remove(fileName);
            GridFSInputFile gfsFile = gfs.createFile(file, true);
            gfsFile.put("uploadDate", Utilities.parseStringDate(dateString));
            gfsFile.setFilename(fileName);
            gfsFile.put("halId", Utilities.getHalIDFromFilename(fileName));
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
            gfsFile.put("uploadDate", Utilities.parseStringDate(dateString));
            gfsFile.put("halId", Utilities.getHalIDFromFilename(fileName));
            gfsFile.setFilename(fileName);
            gfsFile.save();

        } catch (MongoException e) {
            e.printStackTrace();
        }
    }

    public void storeAssetToGridfs(InputStream file, String id, String fileName, String namespace, String dateString) throws ParseException {

        try {
            GridFS gfs = new GridFS(db, namespace);
            BasicDBObject whereQuery = new BasicDBObject();
            whereQuery.put("halId", id);
            gfs.remove(whereQuery);
            //version ?
            GridFSInputFile gfsFile = gfs.createFile(file, true);
            gfsFile.put("uploadDate", Utilities.parseStringDate(dateString));
            gfsFile.setFilename(fileName);
            gfsFile.put("halId", id);
            gfsFile.save();
        } catch (MongoException e) {
            e.printStackTrace();
        }
    }

    public Map<String, List<String>> getFilenames(String collection) throws IOException {
        try {
            GridFS gfs = new GridFS(db, collection);
            // print the result
            DBCursor cursor = gfs.getFileList();
            while (cursor.hasNext()) {
                DBObject dbo = cursor.next();
                String date = Utilities.formatDate((Date) dbo.get("uploadDate"));
                String filename = (String) dbo.get("filename");
                if (filenames.containsKey(date)) {
                    filenames.get(date).add(filename);
                } else {
                    List<String> filens = (new ArrayList<String>());
                    filens.add(filename);
                    filenames.put(date, filens);
                }
            }

        } catch (MongoException e) {
        }
        return filenames;
    }

    public InputStream getFile(String halId, String filename, String collection) {
        InputStream file = null;
        try {
            GridFS gfs = new GridFS(db, collection);
            BasicDBObject whereQuery = new BasicDBObject();
            whereQuery.put("halId", halId);
            whereQuery.put("filename", filename);
            GridFSDBFile cursor = gfs.findOne(whereQuery);
            file = cursor.getInputStream();
        } catch (MongoException e) {
        }
        return file;
    }

    public InputStream streamFile(String filename, String collection) {
        GridFSDBFile file = null;
        try {
            GridFS gfs = new GridFS(db, collection);
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

    public void save(String haldID, String process, String desc) {
        try {
            DBCollection collection = db.getCollection(DIAGNOSTICS);
            BasicDBObject document = new BasicDBObject();
            document.put("haldID", haldID);
            document.put("process", process);
            document.put("desc", desc);
            collection.insert(document);
        } catch (MongoException e) {
        }
    }
}
