package org.sagebionetworks.bridge.models.studies;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Combines the DynamoDB study consent record with the contents of the consent document, 
 * stored on S3. As long as you retrieve a study consent through the StudyConsentService, 
 * it should function as if the document content was persisted with the consent record.
 */
public class StudyConsentView {

    private final StudyConsent consent;
    private final String documentContent;
    
    public StudyConsentView(StudyConsent consent, String documentContent) {
        checkNotNull(consent);
        checkNotNull(documentContent);
        this.consent = consent;
        this.documentContent = documentContent;
    }

    public String getStudyKey() {
        return consent.getStudyKey();
    }

    public long getCreatedOn() {
        return consent.getCreatedOn();
    }

    public boolean getActive() {
        return consent.getActive();
    }

    public String getPath() {
        return consent.getPath();
    }

    public String getStoragePath() {
        return consent.getStoragePath();
    }
    
    public StudyConsent getStudyConsent() {
        return consent;
    }
    
    public String getDocumentContent() {
        return documentContent;
    }
    
}
