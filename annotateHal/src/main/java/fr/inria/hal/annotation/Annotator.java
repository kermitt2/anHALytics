package fr.inria.hal.annotation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Use the NERD REST service for annotating HAL TEI documents. Resulting JSON
 * annotations are then stored in MongoDB as persistent storage.
 *
 * @author Patrice Lopez
 */
public class Annotator {

    private static final Logger logger = LoggerFactory.getLogger(Annotator.class);

    private String nerd_host = null;
    private String nerd_port = null;

    private int nbThreads = 1;

    private final MongoManager mm;

    public Annotator() {
        loadProperties();
        mm = new MongoManager();
    }

    private void loadProperties() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("annotateHal.properties"));
            nerd_host = prop.getProperty("org.annotateHal.nerd_host");
            nerd_port = prop.getProperty("org.annotateHal.nerd_port");
            String threads = prop.getProperty("org.annotateHal.nbThreads");
            try {
                nbThreads = Integer.parseInt(threads);
            } catch (java.lang.NumberFormatException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Failed to load properties: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int annotateCollection() {
        int nb = 0;
        try {
            for (String date : Utilities.getDates()) {
                if (mm.initGridFS(date)) {
                    logger.debug("processing teis for :" + date);
                    while (mm.hasMoreDocuments()) {
                        String filename = mm.getCurrentFilename();
                        String halID = mm.getCurrentHalID();

                        try {
                            // check if the document is already annotated
                            if (mm.isAnnotated()) {
                                logger.debug("skipping " + filename + ": already annotated");
                                mm.nextDocument();
                                continue;
                            }

                            String tei = mm.nextDocument();
                            // filter based on document size... we should actually annotate only 
                            // a given length and then stop
                            if (tei.length() > 300000) {
                                logger.debug("skipping " + filename + ": file too large");
                                continue;
                            }
                            AnnotatorWorker worker
                                    = new AnnotatorWorker(mm, filename, halID, tei, nerd_host, nerd_port);
                            worker.run();
                            nb++;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mm.close();
        }
        return nb;
    }

    public int annotateCollectionMultiThreaded() {
        ThreadPoolExecutor executor = getThreadsExecutor();
        int nb = 0;
        try {
            for (String date : Utilities.getDates()) {
                if (mm.initGridFS(date)) {
                    logger.debug("processing teis for :" + date);
                    while (mm.hasMoreDocuments()) {
                        String filename = mm.getCurrentFilename();
                        String halID = mm.getCurrentHalID();

                        // check if the document is already annotated
                        if (mm.isAnnotated()) {
                            logger.debug("skipping " + filename + ": already annotated");
                            mm.nextDocument();
                            continue;
                        }

                        String tei = mm.nextDocument();
                        // filter based on document size... we should actually annotate only 
                        // a given length and then stop
                        if (tei.length() > 300000) {
                            logger.debug("skipping " + filename + ": file too large");
                            continue;
                        }

                        Runnable worker
                                = new AnnotatorWorker(mm, filename, halID, tei, nerd_host, nerd_port);
                        executor.execute(worker);
                        nb++;
                    }
                }
            }
            executor.shutdown();
            while (!executor.isTerminated()) {
            }
            System.out.println("Finished all threads");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mm.close();
        }
        return nb;
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException,
            InstantiationException, IllegalAccessException {
        String fromDate = null;
        String untilDate = null;
        boolean isMultiThread = false;
        boolean indexingEnabled = false;
        int nbAnnots;
        Annotator annotator = new Annotator();
        String currArg;
        for (int i = 0; i < args.length; i++) {
            currArg = args[i];
            if (currArg.equals("-h")) {
                System.out.println(getHelp());
                continue;
            }
            if (currArg.equals("-dFromDate")) {
                String stringDate = args[i + 1];
                if (!stringDate.isEmpty()) {
                    if (Utilities.isValidDate(stringDate)) {
                        fromDate = args[i + 1];
                    } else {
                        System.err.println("The date given is not correct, make sure it follows the pattern : yyyy-MM-dd");
                        return;
                    }
                }
                i++;
                continue;
            }
            if (currArg.equals("-dUntilDate")) {
                String stringDate = args[i + 1];
                if (!stringDate.isEmpty()) {
                    if (Utilities.isValidDate(stringDate)) {
                        untilDate = stringDate;
                    } else {
                        System.err.println("The date given is not correct, make sure it follows the pattern : yyyy-MM-dd");
                        return;
                    }
                }
                i++;
                continue;
            }
            if (currArg.equals("-multiThread")) {
                isMultiThread = true;
                continue;
            }
            if (currArg.equals("-index")) {
                indexingEnabled = true;
                continue;
            }
        }
        if (untilDate != null || fromDate != null) {
            Utilities.updateDates(fromDate, untilDate);
        }
        // loading based on DocDB XML, with TEI conversion
        try {
            if (isMultiThread) {
                nbAnnots = annotator.annotateCollectionMultiThreaded();
            } else {
                nbAnnots = annotator.annotateCollection();
            }
            logger.debug("Total: " + nbAnnots + " annotations produced.");
            if (indexingEnabled) {
                ElasticSearchManager esm = new ElasticSearchManager();
                // loading based on DocDB XML, with TEI conversion
                try {
                    logger.debug("Total: ");
                    //esm.setUpElasticSearch();
                    //int nbAnnotsIndexed = esm.index();
                    //logger.debug("Total: " + nbAnnotsIndexed + " annotations indexed.");
                } catch (Exception e) {
                    System.err.println("Error when setting-up ElasticSeach cluster");
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("Error when setting-up the annotator.");
            e.printStackTrace();
        }
    }

    private ThreadPoolExecutor getThreadsExecutor() {
        // max queue of tasks of 50 
        BlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<Runnable>(50);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(nbThreads, nbThreads, 60000,
                TimeUnit.MILLISECONDS, blockingQueue);

        // this is for handling rejected tasks (e.g. queue is full)
        executor.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r,
                    ThreadPoolExecutor executor) {
                System.out.println("Task Rejected : "
                        + ((AnnotatorWorker) r).getFilename());
                System.out.println("Waiting for 60 second !!");
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Lets add another time : "
                        + ((AnnotatorWorker) r).getFilename());
                executor.execute(r);
            }
        });
        executor.prestartAllCoreThreads();
        return executor;
    }

    protected static String getHelp() {
        final StringBuffer help = new StringBuffer();
        help.append("HELP ANNOTATE_HAL \n");
        help.append("-h: displays help\n");
        return help.toString();
    }
}
