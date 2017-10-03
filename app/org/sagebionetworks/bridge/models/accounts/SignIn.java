package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.json.SignInDeserializer;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = SignInDeserializer.class)
public final class SignIn implements BridgeEntity {

    private final String email;
    private final String password;
    private final String studyId;
    private final String token;
    private final String reauthToken;
    
    public SignIn(String studyId, String email, String password, String token, String reauthToken) {
        this.studyId = studyId;
        this.email = email;
        this.password = password;
        this.token = token;
        this.reauthToken = reauthToken;
    }
    
    public String getStudyId() {
        return studyId;
    }
    
    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
    
    public String getToken() {
        return token;
    }
    
    public String getReauthToken() {
        return reauthToken;
    }
}
