package fr.inria.hal.indexing;

import com.mongodb.MongoClient;
import com.mongodb.DB;
import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import com.mongodb.gridfs.GridFSDBFile;
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

	private String mongodbServer = null;
	private int mongodbPort;
	
    private DB db = null;
	private GridFS gfs = null;
	
	private List<GridFSDBFile> files = null;
	private int indexFile = 0;
	
    public MongoManager() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("indexHal.properties"));
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
		// open the GridFS
		try {
			gfs = new GridFS(db, TEI_GROBID);
			
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

	public boolean hasMoreDocuments() {
		if (indexFile < files.size())
			return true;
		else 
			return false;
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

}

