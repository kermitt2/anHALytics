package fr.inria.anhalytics.harvest;

import java.io.IOException;
import java.text.ParseException;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 *
 * @author Achraf
 */
interface Harvester {
    /**
     * Harvests the documents submitted on the given date.
     */
    public void fetchDocumentsByDate(String date) throws IOException, SAXException, ParserConfigurationException, ParseException;
    /**
     * Harvests all the repository.
     */
    public void fetchAllDocuments() throws IOException, SAXException, ParserConfigurationException, ParseException;
}
