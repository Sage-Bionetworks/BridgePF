package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("ExternalIdentifier")
@JsonDeserialize(as=DynamoExternalIdentifier.class)
public interface ExternalIdentifier extends BridgeEntity {

    public static ExternalIdentifier create(StudyIdentifier studyId, String externalId) {
        return new DynamoExternalIdentifier(studyId, externalId);
    }
    
    public String getIdentifier();
    public void setIdentifier(String identifier);
    
    public String getStudyId();
    public void setStudyId(String studyId);
    
    public String getHealthCode();
    public void setHealthCode(String healthCode);
    
    public long getReservation();
    public void setReservation(long reservation);
    
}
