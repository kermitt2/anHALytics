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
import java.util.Properties;

public class MongoManager {
    
    public static String filePath = "";
    public String mongodbServer = "";
    public int mongodbPort ;
    public String mongodbDb = "";
    public DB db= null;
    
    public MongoManager() {
        try{
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
    
    public void storeToGridfs(InputStream file, String fileName, String namespace) {

        try {
            GridFS gfs = new GridFS(db, namespace);
            GridFSInputFile gfsFile = gfs.createFile(file, true);
            gfsFile.setFilename(fileName);
            gfsFile.save();

        } catch (MongoException e) {
        }
    }
    
    synchronized public void  storeToGridfs(String filePath, String fileName, String namespace) throws IOException {

        try {
            GridFS gfs = new GridFS(db, namespace);
            File f = new File(filePath);
            GridFSInputFile gfsFile = gfs.createFile(f);
            gfsFile.setFilename(fileName);
            gfsFile.save();

        } catch (MongoException e) {
        }
    }
    
    public String loadFromGridfs(String fileName, String namespace){
        try {
            Mongo mongo = new Mongo(mongodbServer, mongodbPort);
            DB db = mongo.getDB(mongodbDb);
            GridFS gfsPhoto = new GridFS(db, namespace);
            GridFSDBFile documentForOutput = gfsPhoto.findOne(fileName);
            
            documentForOutput.writeTo("/Users/Achraf/tmp/"+fileName);
            
            System.out.println("Done");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return filePath;
    }
}
