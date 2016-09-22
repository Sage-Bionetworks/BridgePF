package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.dynamodb.DynamoFPHSExternalIdentifier;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("ExternalIdentifier")
@JsonDeserialize(as=DynamoFPHSExternalIdentifier.class)
public interface FPHSExternalIdentifier extends BridgeEntity {

    static FPHSExternalIdentifier create(String externalId) {
        return new DynamoFPHSExternalIdentifier(externalId);
    }
    
    String getExternalId();
    void setExternalId(String externalId);
    
    boolean isRegistered();
    void setRegistered(boolean registered);
    
}
