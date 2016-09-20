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
        return new DynamoExternalIdentifier(studyId, identifier);
    }
    
    String getIdentifier();
    void setIdentifier(String identifier);
    
    String getStudyId();
    void setStudyId(String studyId);
    
    String getHealthCode();
    void setHealthCode(String healthCode);
    
    long getReservation();
    void setReservation(long reservation);
    
}
