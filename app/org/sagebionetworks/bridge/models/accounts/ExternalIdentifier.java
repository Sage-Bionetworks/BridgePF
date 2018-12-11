package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("ExternalIdentifier")
@JsonDeserialize(as=DynamoExternalIdentifier.class)
public interface ExternalIdentifier extends BridgeEntity {

    static ExternalIdentifier create(StudyIdentifier studyId, String identifier) {
        return new DynamoExternalIdentifier(studyId == null ? null : studyId.getIdentifier(), identifier);
    }
    
    String getStudyId();
    void setStudyId(String studyId);
    
    String getSubstudyId();
    void setSubstudyId(String substudyId);
    
    String getIdentifier();
    void setIdentifier(String identifier);
    
    String getHealthCode();
    void setHealthCode(String healthCode);
}
