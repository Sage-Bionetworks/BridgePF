package org.sagebionetworks.bridge.models.accounts;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class SignIn implements BridgeEntity {

    private final String username;
    private final String password;
    
    public SignIn(@JsonProperty("username") String username, @JsonProperty("password") String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
    
    public boolean isBlank() {
        return StringUtils.isBlank(username) && StringUtils.isBlank(password);
    }
}
