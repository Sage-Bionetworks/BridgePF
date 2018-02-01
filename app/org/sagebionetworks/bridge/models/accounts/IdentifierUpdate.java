package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class IdentifierUpdate implements BridgeEntity {
    
    private final SignIn signInOrReauthenticate;
    private final String emailUpdate;
    private final Phone phoneUpdate;
    
    @JsonCreator
    public IdentifierUpdate(@JsonProperty("signInOrReauthenticate") SignIn signInOrReauthenticate,
            @JsonProperty("emailUpdate") String emailUpdate, @JsonProperty("phoneUpdate") Phone phoneUpdate) {
        this.signInOrReauthenticate = signInOrReauthenticate;
        this.emailUpdate = emailUpdate;
        this.phoneUpdate = phoneUpdate;
    }
    
    public SignIn getSignInOrReauthenticate() {
        return signInOrReauthenticate;
    }

    public String getEmailUpdate() {
        return emailUpdate;
    }

    public Phone getPhoneUpdate() {
        return phoneUpdate;
    }
    
}
