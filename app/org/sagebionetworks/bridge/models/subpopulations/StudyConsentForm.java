package org.sagebionetworks.bridge.models.subpopulations;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

@BridgeTypeName("StudyConsent")
public class StudyConsentForm implements BridgeEntity {

    private final String documentContent;

    public StudyConsentForm(@JsonProperty("documentContent") String documentContent) {
        this.documentContent = documentContent;
    }

    public String getDocumentContent() {
        return documentContent;
    }

}
