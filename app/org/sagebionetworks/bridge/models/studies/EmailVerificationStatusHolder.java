package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.services.EmailVerificationStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A wrapper for this enumeration as it is sent and received through the API.
 */
@BridgeTypeName("EmailVerificationStatus")
public final class EmailVerificationStatusHolder implements BridgeEntity {
    private final EmailVerificationStatus status;
    
    @JsonCreator
    public EmailVerificationStatusHolder(@JsonProperty("status") EmailVerificationStatus status) {
        this.status = status;
    }

    public EmailVerificationStatus getStatus() {
        return status;
    }
}
