package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = SignIn.Builder.class)
public final class SignIn implements BridgeEntity {

    private final String email;
    private final Phone phone;
    private final String externalId;
    private final String password;
    private final String studyId;
    private final String token;
    private final String reauthToken;
    
    private SignIn(String studyId, String email, Phone phone, String externalId, String password, String token,
            String reauthToken) {
        this.studyId = studyId;
        this.email = email;
        this.phone = phone;
        this.externalId = externalId;
        this.password = password;
        this.token = token;
        this.reauthToken = reauthToken;
    }
    
    // Serializing this property as study allows us to use SignIn to construct the JSON payload that we 
    // are accepting from clients, which is useful for tests to avoid manually constructing the JSON string.
    @JsonProperty("study")
    public String getStudyId() {
        return studyId;
    }
    
    public String getEmail() {
        return email;
    }
    
    public Phone getPhone() {
        return phone;
    }

    public String getExternalId() {
        return externalId;
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
    
    @JsonIgnore
    public AccountId getAccountId() {
        if (email != null) {
            return AccountId.forEmail(studyId, email);
        } else if (phone != null) {
            return AccountId.forPhone(studyId, phone);
        } else if (externalId != null) {
            return AccountId.forExternalId(studyId, externalId);
        }
        throw new IllegalArgumentException("SignIn not constructed with enough information to retrieve an account");
    }
    
    public static class Builder {
        private String username;
        private String email;
        private Phone phone;
        private String externalId;
        private String password;
        private String studyId;
        private String token;
        private String reauthToken;
        
        public Builder withSignIn(SignIn signIn) {
            this.email = signIn.email;
            this.phone = signIn.phone;
            this.externalId = signIn.externalId;
            this.password = signIn.password;
            this.studyId = signIn.studyId;
            this.token = signIn.token;
            this.reauthToken = signIn.reauthToken;
            return this;
        }
        public Builder withUsername(String username) {
            this.username = username;    
            return this;
        }
        public Builder withEmail(String email) {
            this.email = email;
            return this;
        }
        public Builder withPhone(Phone phone) {
            this.phone = phone;
            return this;
        }
        public Builder withExternalId(String externalId) {
            this.externalId = externalId;
            return this;
        }
        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }
        public Builder withStudy(String study) {
            this.studyId = study;
            return this;
        }
        public Builder withToken(String token) {
            this.token = token;
            return this;
        }
        public Builder withReauthToken(String reauthToken) {
            this.reauthToken = reauthToken;
            return this;
        }
        public SignIn build() {
            String identifier = (username != null) ? username : email;
            return new SignIn(studyId, identifier, phone, externalId, password, token, reauthToken);
        }
    }
}