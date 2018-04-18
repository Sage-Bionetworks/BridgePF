package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class PasswordGeneration implements BridgeEntity {

    private final String externalId;
    private final boolean createAccount;
    
    @JsonCreator
    public PasswordGeneration(@JsonProperty("externalId") String externalId,
            @JsonProperty("createAccount") Boolean createAccount) {
        this.externalId = externalId;
        this.createAccount = (createAccount == null) ? true : createAccount;
    }
    
    public String getExternalId() {
        return externalId;
    }

    public boolean isCreateAccount() {
        return createAccount;
    }

}
