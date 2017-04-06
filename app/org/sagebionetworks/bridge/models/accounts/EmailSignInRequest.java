package org.sagebionetworks.bridge.models.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EmailSignInRequest {

    private final String email;
    private final String studyId;
    private final String token;
    
    EmailSignInRequest(@JsonProperty("email") String email, @JsonProperty("study") String studyId,
            @JsonProperty("token") String token) {
        this.email = email;
        this.studyId = studyId;
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public String getStudyId() {
        return studyId;
    }

    public String getToken() {
        return token;
    }
    
}
