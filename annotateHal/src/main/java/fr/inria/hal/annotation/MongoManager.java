package fr.inria.hal.annotation;

import com.mongodb.*;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.util.JSON;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Class for retrieving TEI files from MongoDB GridFS and for storing annotations
 *
 *  @author Patrice Lopez
 */
public class MongoManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoManager.class);

    public static final String BINARY_NAMESPACE = "binarynamespaces";
	public static final String OAI_TEI_NAMESPACE = "oaiteis";
    public static final String TEI_NAMESPACE = "halheader_grobidbody"; // TBR
	public static final String ANNOTATIONS = "annotations"; 

	private String mongodbServer = null;
	private int mongodbPort;
	
	private GridFS gfs = null;
	
	private List<GridFSDBFile> files = null;
	private DB db = null; // DB for source documents
	//private DB db_annot = null; // DB for document annotations
	private DBCursor cursor = null;
	private int indexFile = 0;
	private DBCollection collection = null;
	private MongoClient mongo = null;
	
    public MongoManager() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("annotateHal.properties"));
            mongodbServer = prop.getProperty("org.annotateHal.mongodb_host");
            mongodbPort = Integer.parseInt(prop.getProperty("org.annotateHal.mongodb_port"));
            String mongodbDb = prop.getProperty("org.annotateHal.mongodb_db");
			//String mongodbDbAnnot = prop.getProperty("org.annotateHal.mongodb_db_annot");
            String mongodbUser = prop.getProperty("org.annotateHal.mongodb_user");
            String mongodbPass = prop.getProperty("org.annotateHal.mongodb_pass");
            mongo = new MongoClient(mongodbServer, mongodbPort);
            db = mongo.getDB(mongodbDb);
			//db_annot = mongo.getDB(mongodbDbAnnot);
            boolean auth1 = db.authenticate(mongodbUser, mongodbPass.toCharArray());
			//boolean auth2 = db_annot.authenticate(mongodbUser, mongodbPass.toCharArray());
			initGridFS();
			//initAnnotations();
        } catch (IOException e) {
            e.printStackTrace();
        }
		catch (MongoException e) {
			e.printStackTrace();
		}
    }
	
	public void close() {
		mongo.close();
	}
	
	public DB getDocDB() {
		return db;
	}
	
	public boolean initGridFS() throws MongoException {
		// open the GridFS
		gfs = new GridFS(db, TEI_NAMESPACE); // will be TEI_NAMESPACE
			
		// init the loop
		files = gfs.find(new BasicDBObject());
		indexFile = 0;
		return true;
	}
	
	public boolean initAnnotations() throws MongoException {
		// open the collection
		boolean collectionFound = false;
		Set<String> collections = db.getCollectionNames();
		for(String collection : collections) {
			if (collection.equals(ANNOTATIONS)) {
				collectionFound = true;
			}
		}
		if (!collectionFound) {
			LOGGER.debug("MongoDB collection annotations does not exist and will be created");
		}
		collection = db.getCollection(ANNOTATIONS);
	
		// index on PageID
		collection.ensureIndex(new BasicDBObject("filename", 1));  
		
		// init the loop
		cursor = collection.find();
		
		return true;
	}

	public boolean hasMoreDocuments() {
		if (indexFile < files.size())
			return true;
		else 
			return false;
	}
	
	public boolean hasMoreAnnotations() {
		if (cursor == null) {
			// init the loop
			cursor = collection.find();	
		}
		
		if (!cursor.hasNext()) {
			cursor.close();
			return false;
		}
		else 
			return true;
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
		} 
		catch (MongoException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		return tei;
	}

	public String nextAnnotation() {
		String json  = null;
		try {
			DBObject annotations = cursor.next();			
			BasicDBList nerd = (BasicDBList)annotations.get("nerd");
			json = nerd.toString();
			/*if (!cursor.hasNext()) {
				cursor.close();
			}*/
		} 
		catch (MongoException e) {
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
				halID = filename.substring(0,ind);
				// we still have possibly the version information
				ind = halID.indexOf("v");
				halID = halID.substring(0,ind);
            }
		} 
		catch (MongoException e) {
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
		} 
		catch (MongoException e) {
			e.printStackTrace();
		}
		return filename;
	}

	public boolean insertAnnotation(String json) {
		if (collection == null) {
			collection = db.getCollection("annotations");	
			collection.ensureIndex(new BasicDBObject("filename", 1));
			collection.ensureIndex(new BasicDBObject("xml:id", 1)); 
		}
		DBObject dbObject = (DBObject)JSON.parse(json);
		WriteResult result = collection.insert(dbObject);
		CommandResult res = result.getCachedLastError();
		if ((res != null) && (res.ok()))
			return true;
		else 
			return false;
	}
	
	public void insertDocument(String filename, String content) {
        try {
            GridFS gfs = new GridFS(db, TEI_NAMESPACE);
            gfs.remove(filename);
			byte[] b = content.getBytes(Charset.forName("UTF-8"));
            GridFSInputFile gfsFile = gfs.createFile(b);
            gfsFile.setFilename(filename);
            gfsFile.save();

        } catch (MongoException e) {
            e.printStackTrace();
        }
	}
	
	public void removeDocument(String filename) {
		try {
            GridFS gfs = new GridFS(db, TEI_NAMESPACE);
            gfs.remove(filename);
		} catch (MongoException e) {
            e.printStackTrace();
        }
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
 	   	try {
			DBCursor cursor = collection.find(query);
			if (cursor.hasNext())
				result = true;
			else 
				result = false;
		}
		finally {
			cursor.close();
		}
		return result;
	}

}

