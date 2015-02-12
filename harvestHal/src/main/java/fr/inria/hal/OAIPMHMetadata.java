package fr.inria.hal;

import org.w3c.dom.NodeList;

/**
 *
 * @author Achraf
 */
public interface OAIPMHMetadata {
    public final static String ListRecordsElement = "ListRecords";
    public final static String RecordElement = "record";
    public final static String TeiElement = "metadata";
    public final static String IdElement = "identifier";
    public final static String TypeElement = "setSpec";
    public final static String ResumptionToken = "resumptionToken";
    public final static String FileUrlElement = "ref";
    public final static String RefPATH = "metadata/TEI/text/body/listBibl/biblFull/publicationStmt/idno[@type='halRef']";
    public final static String DoiPATH = "metadata/TEI/text/body/listBibl/biblFull/sourceDesc/biblStruct/idno[@type='doi']";
    
    enum ConsideredTypes {

        ART, COMM, OUV, POSTER, DOUV, PATENT, REPORT, THESE, HDR, LECTURE, COUV, OTHER, UNDEFINED
    };
    
    public String getTei(NodeList tei);
    public String getId(NodeList tei);
    public String getDocumentType(NodeList tei);
     public String getFileUrl(NodeList tei);
}