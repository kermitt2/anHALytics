package fr.inria.hal.indexing;

import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.*;
import com.mongodb.util.JSON;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 *  Class for retrieving TEI files to be indexed from MongoDB GridFS
 * 
 */
public class MongoManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoManager.class);

    public static final String BINARY = "binarynamespaces";
	public static final String OAI_TEI = "oaiteis";
	public static final String TEI_GROBID = "teigrobidnamespaces"; // TBR
    public static final String TEI_FINAL = "halheader_grobidbody"; // TBR
	public static final String ANNOTATIONS = "annotations"; 

	private String mongodbServer = null;
	private int mongodbPort;
	
    private DB db = null;
	private GridFS gfs = null;
	
	private List<GridFSDBFile> files = null;
	private int indexFile = 0;
	
	// for annotations
	private DBCursor cursor = null;
	private DBCollection collection = null;
	
	private MongoClient mongo = null;
	
    public MongoManager() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("indexHal.properties"));
            mongodbServer = prop.getProperty("org.indexHal.mongodb_host");
            mongodbPort = Integer.parseInt(prop.getProperty("org.indexHal.mongodb_port"));
            String mongodbDb = prop.getProperty("org.indexHal.mongodb_db");
            String mongodbUser = prop.getProperty("org.indexHal.mongodb_user");
            String mongodbPass = prop.getProperty("org.indexHal.mongodb_pass");
            mongo = new MongoClient(mongodbServer, mongodbPort);
            db = mongo.getDB(mongodbDb);
            boolean auth = db.authenticate(mongodbUser, mongodbPass.toCharArray());
			
			initAnnotations();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	public void close() {
		mongo.close();
	}
	
	public boolean init() throws Exception {
		// open the GridFS
		try {
			gfs = new GridFS(db, TEI_FINAL);
			
			// init the loop
			files = gfs.find(new BasicDBObject());
			indexFile = 0;
		}
		catch(Exception e) {
			LOGGER.debug("Cannot retrieve MongoDB TEI doc GridFS.");
			throw new Exception(e);
		}
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
	
		// index on filename and xml:id
		collection.ensureIndex(new BasicDBObject("filename", 1));  
		collection.ensureIndex(new BasicDBObject("xml:id", 1)); 
		
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
		return cursor.hasNext();
	}

	public String next() {
		String tei = null;
		try {
			if (indexFile < files.size()) {
                GridFSDBFile teifile = files.get(indexFile);
				InputStream input = teifile.getInputStream();
				tei = IOUtils.toString(input, "UTF-8");
				indexFile++;
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
			if (cursor == null) {
				// init the loop
				cursor = collection.find();	
			}
			
			DBObject obj = cursor.next();
			json = obj.toString();
			
			if (!cursor.hasNext()) {
				cursor.close();
			}
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

	/**
	 * Add a TEI document in the GridFS
	 */
	public void addDocument(String filePath, String fileName, Date date) 
		throws IOException, ParseException {
        try {
            GridFS gfs = new GridFS(db, TEI_FINAL);
            File f = new File(filePath);
            gfs.remove(fileName);
            GridFSInputFile gfsFile = gfs.createFile(f);
            gfsFile.put("uploadDate", date);
            gfsFile.setFilename(fileName);
            gfsFile.save();

        } catch (MongoException e) {
            e.printStackTrace();
        }
	}
	
	public void removeDocument(String filename) {
		try {
            GridFS gfs = new GridFS(db, TEI_FINAL);
            gfs.remove(filename);
		} catch (MongoException e) {
            e.printStackTrace();
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
				BasicDBList nerd = (BasicDBList)annotations.get("nerd");
			    for (int i = 0; i < nerd.size(); i++) {
			        BasicDBObject annotation = (BasicDBObject) nerd.get(i);
			        String theId = annotation.getString("xml:id");
					if ((theId != null) && (theId.equals(id)) ) {
						result = annotation.toString();
						break;
					}
				}
			}
		}
		finally {
			if (curs != null)
				curs.close();
		}
		return result;
	}

}

