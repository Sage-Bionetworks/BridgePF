package org.sagebionetworks.bridge.models.subpopulations;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Combines the DynamoDB study consent record with the contents of the consent document, 
 * stored on S3. As long as you retrieve a study consent through the StudyConsentService, 
 * it should function as if the document content was persisted with the consent record.
 */
@BridgeTypeName("StudyConsent")
public class StudyConsentView {

    private final StudyConsent consent;
    private final String documentContent;
    
    public StudyConsentView(StudyConsent consent, String documentContent) {
        checkNotNull(consent);
        checkNotNull(documentContent);
        this.consent = consent;
        this.documentContent = documentContent;
    }

    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getCreatedOn() {
        return consent.getCreatedOn();
    }

    public boolean getActive() {
        return consent.getActive();
    }

    @JsonIgnore
    public StudyConsent getStudyConsent() {
        return consent;
    }
    
    public String getDocumentContent() {
        return documentContent;
    }

}
