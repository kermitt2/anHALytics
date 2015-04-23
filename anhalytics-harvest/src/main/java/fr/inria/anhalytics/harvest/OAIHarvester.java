package fr.inria.anhalytics.harvest;

import fr.inria.anhalytics.commons.data.PubFile;
import fr.inria.anhalytics.commons.exceptions.BinaryNotAvailableException;
import fr.inria.anhalytics.commons.data.TEI;
import fr.inria.anhalytics.commons.managers.MongoManager;
import fr.inria.anhalytics.commons.utilities.Utilities;
import java.io.*;
import java.text.ParseException;
import java.util.*;
import javax.xml.parsers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.*;

public class OAIHarvester implements Harvester {

    private static final Logger logger = LoggerFactory.getLogger(OAIHarvester.class);

    private static String OAI_FORMAT = "xml-tei";
    private ArrayList<String> fields = null;
    private ArrayList<String> affiliations = null;

    private MongoManager mm;
    private final OAIPMHDom oaiDom;

    private String oai_url = null;

    public OAIHarvester(MongoManager mm, String oai_url) {
        this.mm = mm;
        this.oai_url = oai_url;
        oaiDom = new OAIPMHDom();

        fields = new ArrayList<String>();
        affiliations = new ArrayList<String>();

        affiliations.add("INRIA");

    }

    @Override
    public void fetchDocumentsByDate(String date) throws ParserConfigurationException, IOException {
        boolean stop = false;
        String tokenn = null;
        while (!stop) {
            String request = oai_url + "/?verb=ListRecords&metadataPrefix=" + OAI_FORMAT + "&from=" + date + "&until=" + date;

            if (tokenn != null) {
                request = oai_url + "/?verb=ListRecords&resumptionToken=" + tokenn;
            }
            System.out.println(request);
            logger.debug("Sending: " + request);

            InputStream in = Utilities.request(request, true);
            logger.debug("\t Extracting teis.... for " + date);
            List<TEI> teis = oaiDom.getTeis(in);

            processTeis(teis, date);

            // token if any:
            tokenn = oaiDom.getToken();
            if (tokenn == null) {
                stop = true;
            }
        }
    }

    @Override
    public void fetchAllDocuments() throws ParserConfigurationException, IOException {
        for (String date : Utilities.getDates()) {
            fetchDocumentsByDate(date);
        }
    }

    /*
     * Stores the given teis and downloads attachements(main file(s), annexes ..). 
     */
    private void processTeis(List<TEI> teis, String date) {
        for (TEI tei : teis) {
            try {
                String teiFilename = tei.getId() + ".tei.xml";
                logger.debug("\t\t Extracting tei.... for " + tei.getId());
                String teiString = tei.getTei();
                if (teiString.length() > 0) {
                    logger.debug("\t\t\t\t Storing tei : " + tei.getId());
                    mm.addDocument(new ByteArrayInputStream(teiString.getBytes()), teiFilename, MongoManager.ADDITIONAL_TEIS, date);

                    String filename = tei.getId() + ".pdf";
                    if (!mm.isCollected(filename)) {
                        //binary processing.
                        if (tei.getFile() != null) {
                            System.out.println(filename);
                            downloadFile(tei.getFile(), tei.getId(), date);
                        } else {
                            mm.save(tei.getId(), "harvestProcess", "no file url", null);
                            logger.debug("\t\t\t PDF not found !");
                        }
                    }
                    //annexes
                    for (PubFile file : tei.getAnnexes()) {
                        downloadFile(file, tei.getId(), date);
                        // diagnose annexes (not found)?
                    }
                } else {
                    logger.debug("\t\t\t Tei not found !!!");
                }
            } catch (BinaryNotAvailableException bna) {
                mm.save(tei.getId(), "harvestProcess", "file not available", null);
            } catch (Exception e) {
                mm.save(tei.getId(), "harvestProcess", "harvest error", null);
                e.printStackTrace();
            }
        }
    }

    /**
     * Downloads the given file and classify it either as main file or as an
     * annex.
     */
    private void downloadFile(PubFile file, String id, String date) throws ParseException, IOException {
        InputStream inBinary = null;
        Date embDate = Utilities.parseStringDate(file.getEmbargoDate());
        Date today = new Date();
        if (embDate.before(today) || embDate.equals(today)) {
            logger.debug("\t\t\t Downloading: " + file.getUrl());
            inBinary = Utilities.request(file.getUrl(), false);
            if (inBinary == null) {
                mm.save(id, "no stream/"+file.getType(), file.getUrl(), date);
            } else {
                if ((file.getType()).equals("file")) {
                    mm.addDocument(inBinary, id + ".pdf", MongoManager.BINARIES, date);
                } else {
                    int n = file.getUrl().lastIndexOf("/");
                    String filename = file.getUrl().substring(n + 1);
                    System.out.println(filename);
                    mm.addAnnexDocument(inBinary, file.getType(), id, filename, MongoManager.PUB_ANNEXES, date);
                }
                inBinary.close();
            }
        } else {
            mm.save(id, "embargo", file.getUrl(), file.getEmbargoDate());
            logger.debug("\t\t\t file under embargo !");
        }
    }
}
