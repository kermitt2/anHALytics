package fr.inria.anhalytics.commons.managers;

import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.*;
import com.mongodb.util.JSON;
import fr.inria.anhalytics.commons.data.TEI;
import fr.inria.anhalytics.commons.utilities.Utilities;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for retrieving TEI files to be indexed from MongoDB GridFS
 *
 */
public class MongoManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoManager.class);

    public static final String DIAGNOSTICS = "diagnostics";
    public static final String HAL_TEIS = "hal_teis";
    public static final String HAL_BINARIES = "hal_binaries";
    public static final String HALHEADER_GROBIDBODY_TEIS = "halheader_grobidbody_teis";
    public static final String GROBID_TEIS = "grobid_teis";
    public static final String ASSETS = "assets";

    public static final String ANNOTATIONS = "annotations";

    private String mongodbServer = null;
    private int mongodbPort;

    private DB db = null;
    private GridFS gfs = null;

    private List<GridFSDBFile> files = null;
    private int indexFile = 0;

    private String currentAnnotationFilename = null;
    private String currentAnnotationHalID = null;

    // for annotations
    private DBCursor cursor = null;
    private DBCollection collection = null;

    private MongoClient mongo = null;

    public MongoManager() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("commons.properties"));
            mongodbServer = prop.getProperty("commons.mongodb_host");
            mongodbPort = Integer.parseInt(prop.getProperty("commons.mongodb_port"));
            String mongodbDb = prop.getProperty("commons.mongodb_db");
            String mongodbUser = prop.getProperty("commons.mongodb_user");
            String mongodbPass = prop.getProperty("commons.mongodb_pass");
            mongo = new MongoClient(mongodbServer, mongodbPort);
            db = mongo.getDB(mongodbDb);
            boolean auth = db.authenticate(mongodbUser, mongodbPass.toCharArray());

            //initAnnotations();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        mongo.close();
    }

    public boolean init(String collection, String date) throws Exception {
        // open the GridFS
        try {
            gfs = new GridFS(db, collection);

            // init the loop
            BasicDBObject bdbo = new BasicDBObject();
            if (date != null) {
                bdbo.append("uploadDate", Utilities.parseStringDate(date));
            }
            files = gfs.find(bdbo);
            indexFile = 0;
        } catch (Exception e) {
            LOGGER.debug("Cannot retrieve MongoDB TEI doc GridFS.");
            throw new Exception(e);
        }
        return true;
    }

    public boolean initAnnotations() throws MongoException {
        // open the collection
        boolean collectionFound = false;
        Set<String> collections = db.getCollectionNames();
        for (String collection : collections) {
            if (collection.equals(ANNOTATIONS)) {
                collectionFound = true;
            }
        }
        if (!collectionFound) {
            LOGGER.debug("MongoDB collection annotations does not exist and will be created");
        }
        collection = db.getCollection(ANNOTATIONS);

        // index on filename and xml:id
        collection.ensureIndex(new BasicDBObject("filename", 1));
        collection.ensureIndex(new BasicDBObject("xml:id", 1));

        return true;
    }

    public boolean hasMoreDocuments() {
        if (indexFile < files.size()) {
            return true;
        } else {
            return false;
        }
    }

    public boolean hasMoreAnnotations() {
        if (cursor == null) {
            // init the loop
            cursor = collection.find();
        }
        return cursor.hasNext();
    }

    public String nextAnnotation() {
        String json = null;
        try {
            if (cursor == null) {
                // init the loop
                cursor = collection.find();
            }

            DBObject obj = cursor.next();
            json = obj.toString();

            if (!cursor.hasNext()) {
                cursor.close();
            }
        } catch (MongoException e) {
            e.printStackTrace();
        }

        return json;
    }

    public String getCurrentHalID() {
        String halID = null;
        try {
            if (indexFile < files.size()) {
                GridFSDBFile teifile = files.get(indexFile);
                String filename = teifile.getFilename();
                int ind = filename.indexOf(".");
                halID = filename.substring(0, ind);
                // we still have possibly the version information
                ind = halID.indexOf("v");
                halID = halID.substring(0, ind);
            }
        } catch (MongoException e) {
            e.printStackTrace();
        }
        return halID;
    }

    public String getCurrentFilename() {
        String filename = null;
        try {
            if (indexFile < files.size()) {
                GridFSDBFile teifile = files.get(indexFile);
                filename = teifile.getFilename();
            }
        } catch (MongoException e) {
            e.printStackTrace();
        }
        return filename;
    }

    public String nextDocument() {
        String tei = null;
        try {
            if (indexFile < files.size()) {
                GridFSDBFile teifile = files.get(indexFile);
                InputStream input = teifile.getInputStream();
                tei = IOUtils.toString(input, "UTF-8");
                indexFile++;
                input.close();
            }
        } catch (MongoException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return tei;
    }
    
    public InputStream nextBinaryDocument() {
        InputStream input = null;
        try {
            if (indexFile < files.size()) {
                GridFSDBFile teifile = files.get(indexFile);
                input = teifile.getInputStream();
                indexFile++;
            }
        } catch (MongoException e) {
            e.printStackTrace();
        }
        return input;
    }

    public String getCurrentAnnotationFilename() {
        return currentAnnotationFilename;
    }

    public String getCurrentAnnotationHalID() {
        return currentAnnotationHalID;
    }

    public void removeDocument(String filename) {
        try {
            GridFS gfs = new GridFS(db, HALHEADER_GROBIDBODY_TEIS);
            gfs.remove(filename);
        } catch (MongoException e) {
            e.printStackTrace();
        }
    }

    public boolean insertAnnotation(String json) {
        if (collection == null) {
            collection = db.getCollection("annotations");
            collection.ensureIndex(new BasicDBObject("filename", 1));
            collection.ensureIndex(new BasicDBObject("xml:id", 1));
        }
        DBObject dbObject = (DBObject) JSON.parse(json);
        WriteResult result = collection.insert(dbObject);
        CommandResult res = result.getCachedLastError();
        if ((res != null) && (res.ok())) {
            return true;
        } else {
            return false;
        }
    }

    public String getAnnotation(String filename, String id) {
        if (collection == null) {
            collection = db.getCollection("annotations");
            collection.ensureIndex(new BasicDBObject("filename", 1));
            collection.ensureIndex(new BasicDBObject("xml:id", 1));
        }
        String result = null;
        BasicDBObject query = new BasicDBObject("filename", filename);
        DBCursor curs = null;
        try {
            curs = collection.find(query);
            if (curs.hasNext()) {
                // we get now the sub-document corresponding to the given id
                DBObject annotations = curs.next();
                BasicDBList nerd = (BasicDBList) annotations.get("nerd");
                for (int i = 0; i < nerd.size(); i++) {
                    BasicDBObject annotation = (BasicDBObject) nerd.get(i);
                    String theId = annotation.getString("xml:id");
                    if ((theId != null) && (theId.equals(id))) {
                        result = annotation.toString();
                        break;
                    }
                }
            }
        } finally {
            if (curs != null) {
                curs.close();
            }
        }
        return result;
    }

    public String getAnnotation(String filename) {
        if (collection == null) {
            collection = db.getCollection("annotations");
            collection.ensureIndex(new BasicDBObject("filename", 1));
            collection.ensureIndex(new BasicDBObject("xml:id", 1));
        }
        String result = null;
        BasicDBObject query = new BasicDBObject("filename", filename);
        DBCursor curs = null;
        try {
            curs = collection.find(query);
            if (curs.hasNext()) {
                // we get now the sub-document corresponding to the given id
                DBObject annotations = curs.next();
                BasicDBList nerd = (BasicDBList) annotations.get("nerd");
                result = nerd.toString();
            }
        } finally {
            if (curs != null) {
                curs.close();
            }
        }
        return result;
    }

    /**
     * Check if the current document has already been annotated.
     */
    public boolean isAnnotated() {
        if (collection == null) {
            collection = db.getCollection("annotations");
            collection.ensureIndex(new BasicDBObject("filename", 1));
            collection.ensureIndex(new BasicDBObject("xml:id", 1));
        }
        boolean result = false;
        String filename = getCurrentFilename();
        BasicDBObject query = new BasicDBObject("filename", filename);
        DBCursor cursor = null;
        try {
            cursor = collection.find(query);
            if (cursor.hasNext()) {
                result = true;
            } else {
                result = false;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    /**
     * Add a TEI/PDF document in the GridFS
     */
    public void addDocument(InputStream file, String fileName, String namespace, String dateString) throws ParseException {

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

    /**
     * Add a document assets with corresponding desc in the GridFS (otherwise we
     * could do it with ES ?)
     */
    public void addAssetDocument(InputStream file, String id, String fileName, String namespace, String dateString) throws ParseException {

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

    /*
      Returns the asset files using the halID+filename indexes.
    */
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
            GridFS gfs = new GridFS(db, HAL_BINARIES);
            file = gfs.findOne(filename);

        } catch (MongoException e) {
        }
        return file.getInputStream();
    }

    public void save(String haldID, String process, String desc) {
        try {
            DBCollection collection = db.getCollection(DIAGNOSTICS);
            BasicDBObject whereQuery = new BasicDBObject();
            whereQuery.put("halId", haldID);
            whereQuery.put("process", process);
            collection.remove(whereQuery);
            BasicDBObject document = new BasicDBObject();
            document.put("haldID", haldID);
            document.put("process", process);
            document.put("desc", desc);
            collection.insert(document);
        } catch (MongoException e) {
        }
    }
}
