package fr.inria.hal;

/**
 *
 * @author Achraf
 */
public class TEI {
    
    public TEI(){}
    public TEI(String id, String tei, String documentType, String fileUrl){
        this.id = id;
        this.tei = tei;
        this.documentType = documentType;
        this.fileUrl = fileUrl;
    }
    
    private String id;
    private String tei;
    private String documentType;
    private String fileUrl;

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the tei
     */
    public String getTei() {
        return tei;
    }

    /**
     * @param tei the tei to set
     */
    public void setTei(String tei) {
        this.tei = tei;
    }

    /**
     * @return the documentType
     */
    public String getDocumentType() {
        return documentType;
    }

    /**
     * @param documentType the documentType to set
     */
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    /**
     * @return the fileUrl
     */
    public String getFileUrl() {
        return fileUrl;
    }

    /**
     * @param fileUrl the fileUrl to set
     */
    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }
}
