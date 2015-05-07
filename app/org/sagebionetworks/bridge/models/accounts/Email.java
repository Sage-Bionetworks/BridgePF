package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Email implements BridgeEntity {

    private final String email;
    private final StudyIdentifier studyIdentifier;
    
    public Email(@JsonProperty("study") String study, @JsonProperty("email") String email) {
        this.studyIdentifier = (study != null) ? new StudyIdentifierImpl(study) : null;
        this.email = email;
    }

    public Email(StudyIdentifier studyId, String email) {
        this.studyIdentifier = studyId;
        this.email = email;
    }
    
    public String getEmail() {
        return email;
    }
    
    public StudyIdentifier getStudyIdentifier() {
        return studyIdentifier;
    }

}
