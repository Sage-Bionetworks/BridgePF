package org.sagebionetworks.bridge.models.accounts;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.json.SignInDeserializer;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = SignInDeserializer.class)
public final class SignIn implements BridgeEntity {

    private final String email;
    private final String password;
    
    public SignIn(String email, String password) {
        this.email = email;
        this.password = password;
    }
    
    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
    
    public boolean isBlank() {
        return StringUtils.isBlank(email) && StringUtils.isBlank(password);
    }
}
