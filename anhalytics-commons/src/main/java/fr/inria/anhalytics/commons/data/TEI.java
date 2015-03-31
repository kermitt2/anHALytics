package fr.inria.anhalytics.commons.data;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Achraf
 */
public class TEI {
    
    public TEI(){}
    public TEI(String id, String tei, String doi, String documentType, String fileUrl, String ref){
        this.id = id;
        this.tei = tei;
        this.documentType = documentType;
        this.fileUrl = fileUrl;
        this.doi = doi;
        this.ref = ref;
    }
    
    private String id;
    private String doi;
    private String tei;
    private String documentType;
    private String fileUrl;
    private String ref;
    private List<String> subjects;

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

    /**
     * @return the subjects
     */
    public List<String> getSubjects() {
        return subjects;
    }

    /**
     * @param subjects the subjects to set
     */
    public void setSubjects(List<String> subjects) {
        this.subjects = subjects;
    }

    /**
     * @return the doi
     */
    public String getDoi() {
        return doi;
    }

    /**
     * @param doi the doi to set
     */
    public void setDoi(String doi) {
        this.doi = doi;
    }

    /**
     * @return the ref
     */
    public String getRef() {
        return ref;
    }

    /**
     * @param ref the ref to set
     */
    public void setRef(String ref) {
        this.ref = ref;
    }
    
    public void addSubject(String subject){
        if(subjects == null)
            subjects = new ArrayList<String>();
        subjects.add(subject);
    }
}
