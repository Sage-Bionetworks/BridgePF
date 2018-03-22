package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Identifier implements BridgeEntity {

    private final StudyIdentifier studyIdentifier;
    private final String email;
    private final Phone phone;
    
    public Identifier(@JsonProperty("study") String study, @JsonProperty("email") String email,
            @JsonProperty("phone") Phone phone) {
        this.studyIdentifier = (study != null) ? new StudyIdentifierImpl(study) : null;
        this.email = email;
        this.phone = phone;
    }

    public Identifier(StudyIdentifier studyId, String email) {
        this.studyIdentifier = studyId;
        this.email = email;
        this.phone = null;
    }
    
    public Identifier(StudyIdentifier studyId, Phone phone) {
        this.studyIdentifier = studyId;
        this.phone = phone;
        this.email = null;
    }
    
    public String getEmail() {
        return email;
    }
    
    public Phone getPhone() {
        return phone;
    }
    
    public StudyIdentifier getStudyIdentifier() {
        return studyIdentifier;
    }

}
