package org.sagebionetworks.bridge.models.accounts;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("ExternalIdentifier")
@JsonDeserialize(as=DynamoExternalIdentifier.class)
public interface ExternalIdentifier extends BridgeEntity {

    static ExternalIdentifier create(StudyIdentifier studyId, String identifier) {
        checkNotNull(studyId);
        return new DynamoExternalIdentifier(studyId.getIdentifier(), identifier);
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
