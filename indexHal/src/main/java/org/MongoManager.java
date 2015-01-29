package org.indexHal;

import com.mongodb.MongoClient;
import com.mongodb.DB;
import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import com.mongodb.WriteConcern;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.ServerAddress;
import com.mongodb.WriteResult;
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
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoManager.class);

    public static final String BINARY_NAMESPACE = "binarynamespaces";
    public static final String TEI_NAMESPACE = "halheader_grobidbody"; // TBR

	private String mongodbServer = null;
	private int mongodbPort;
	
    private DB db = null;
	private MongoClient mongo = null;
	private DBCollection collectionTEI = null;
	
	private int nbRemainingDoc = 0;

    public MongoManager() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("horg.indextHal.properties"));
            mongodbServer = prop.getProperty("org.indexHal.mongodb_host");
            mongodbPort = Integer.parseInt(prop.getProperty("org.indexHal.mongodb_port"));
            String mongodbDb = prop.getProperty("org.indexHal.mongodb_db");
            String mongodbUser = prop.getProperty("org.indexHal.mongodb_user");
            String mongodbPass = prop.getProperty("org.indexHal.mongodb_pass");
            MongoClient mongo = new MongoClient(mongodbServer, mongodbPort);
            db = mongo.getDB(mongodbDb);
            boolean auth = db.authenticate(mongodbUser, mongodbPass.toCharArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	public boolean init() throws Exception {
		try {
			mongo = new MongoClient(mongodbServer, 
									mongodbPort);
		}
		catch(Exception e) {
			LOGGER.debug("Cannot open a client to MongoDB.");
			throw new Exception(e);
		}
		
		// open the collection
		try {
			// collection
			boolean collectionFound = false;
			Set<String> collections = db.getCollectionNames();
			for(String collection : collections) {
				if (collection.equals(TEI_NAMESPACE)) {
					collectionFound = true;
				}
			}
			if (!collectionFound) {
				LOGGER.debug("MongoDB collection for TEI documents does not exist");
				return false;
			}
			collectionTEI = db.getCollection(TEI_NAMESPACE);
		
			// index on PageID
			collectionTEI.ensureIndex(new BasicDBObject("filename", 1));  
		}
		catch(Exception e) {
			LOGGER.debug("Cannot retrieve MongoDB TEI doc collection.");
			throw new Exception(e);
		}
		return true;
	}

	public boolean hasMoreDocuments() {
		boolean result = false;
		
		return result;
	}

	public String next() {
		String tei = null;
		
		return tei;
	}
	
	public void closeMongoDB() {
		mongo.close();
	}
}

