package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = SignIn.Builder.class)
public final class SignIn implements BridgeEntity {

    private final String email;
    private final String phone;
    private final String phoneRegion;
    private final String password;
    private final String studyId;
    private final String token;
    private final String reauthToken;
    
    private SignIn(String studyId, String email, String phone, String phoneRegion, String password, String token, String reauthToken) {
        this.studyId = studyId;
        this.email = email;
        this.phone = phone;
        this.phoneRegion = phoneRegion;
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
    
    public String getPhone() {
        return phone;
    }

    public String getPhoneRegion() {
        return phoneRegion;
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
    
    public static class Builder {
        private String username;
        private String email;
        private String phone;
        private String phoneRegion;
        private String password;
        private String studyId;
        private String token;
        private String reauthToken;
        
        public Builder withUsername(String username) {
            this.username = username;    
            return this;
        }
        public Builder withEmail(String email) {
            this.email = email;
            return this;
        }
        public Builder withPhone(String phone) {
            this.phone = phone;
            return this;
        }
        public Builder withPhoneRegion(String phoneRegion) {
            this.phoneRegion = phoneRegion;
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
            return new SignIn(studyId, identifier, phone, phoneRegion, password, token, reauthToken);
        }
    }
}