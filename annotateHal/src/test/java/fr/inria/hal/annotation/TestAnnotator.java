package fr.inria.hal.annotation;

import org.junit.Test;

import java.io.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.apache.commons.io.FileUtils;
import org.xml.sax.InputSource;
import java.io.File;

/**
 * @author Patrice Lopez
 */
public class TestAnnotator {

    public File getResourceDir(String resourceDir) throws Exception {
        File file = new File(resourceDir);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw new Exception("Cannot start test, because test resource folder is not correctly set.");
            }
        }
        return (file);
    }

    //@Test
    public void testAnnotateFullText() throws Exception {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
        Annotator annotator = new Annotator();
        DocumentBuilder docBuilder = null;
        MongoManager mm = null;

        try {
            File teiFile = new File(this.getResourceDir("src/test/resources/").getPath() + "/hal-01110586v1.final.tei.xml");
            String tei = FileUtils.readFileToString(teiFile, "UTF-8");

            docBuilder = docFactory.newDocumentBuilder();
            mm = new MongoManager();

            Document docTei = docBuilder.parse(new InputSource(new ByteArrayInputStream(tei.getBytes("utf-8"))));
            String json = annotator.annotateDocument(docTei, "1", "1");
            mm.insertAnnotation(json);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            File teiFile = new File(this.getResourceDir("src/test/resources/").getPath() + "/hal-01110668v1.final.tei.xml");
            String tei = FileUtils.readFileToString(teiFile, "UTF-8");

            Document docTei = docBuilder.parse(new InputSource(new ByteArrayInputStream(tei.getBytes("utf-8"))));
            String json = annotator.annotateDocument(docTei, "2", "2");

            mm.insertAnnotation(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //@Test
    public void testAnnotateCollection() throws Exception {
        // insert two documents
        MongoManager mm = null;
        Annotator annotator = new Annotator();
        try {
            mm = new MongoManager();

            File teiFile = new File(this.getResourceDir("src/test/resources/").getPath() + "/hal-01110586v1.final.tei.xml");
            String tei = FileUtils.readFileToString(teiFile, "UTF-8");
            mm = new MongoManager();
            mm.insertDocument("hal-01110586v1.final.tei.xml", tei);

            teiFile = new File(this.getResourceDir("src/test/resources/").getPath() + "/hal-01110668v1.final.tei.xml");
            tei = FileUtils.readFileToString(teiFile, "UTF-8");
            mm.insertDocument("hal-01110668v1.final.tei.xml", tei);

            int nb = annotator.annotateCollection();
            System.out.println(nb + " documents annotated");

            // remove the documents
            mm.removeDocument("hal-01110668v1.final.tei.xml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testAnnotateCollectionMultiThreaded() throws Exception {
        // insert two documents
        MongoManager mm = null;
        Annotator annotator = new Annotator();
        try {
            mm = new MongoManager();

            File teiFile = new File(this.getResourceDir("src/test/resources/").getPath() + "/hal-01110586v1.final.tei.xml");
            String tei = FileUtils.readFileToString(teiFile, "UTF-8");
            mm = new MongoManager();
            mm.insertDocument("hal-01110586v1.final.tei.xml", tei);

            teiFile = new File(this.getResourceDir("src/test/resources/").getPath() + "/hal-01110668v1.final.tei.xml");
            tei = FileUtils.readFileToString(teiFile, "UTF-8");
            mm.insertDocument("hal-01110668v1.final.tei.xml", tei);

            int nb = annotator.annotateCollectionMultiThreaded();
            System.out.println(nb + " documents annotated");

            // remove the documents
            mm.removeDocument("hal-01110668v1.final.tei.xml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
