package org.sagebionetworks.bridge.models.accounts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class GeneratedPassword {
    private final String externalId;
    private final String userId;
    private final String password;

    @JsonCreator
    public GeneratedPassword(@JsonProperty("externalId") String externalId, @JsonProperty("userId") String userId,
            @JsonProperty("password") String password) {
        this.externalId = externalId;
        this.userId = userId;
        this.password = password;
    }
    public String getExternalId() {
        return externalId;
    }
    public String getUserId() {
        return userId;
    }
    public String getPassword() {
        return password;
    }
}
