package org.sagebionetworks.bridge.models.itp;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = IntentToParticipate.Builder.class)
public class IntentToParticipate implements BridgeEntity {
    private final String study;
    private final String email;
    private final String phone;
    private final String subpopGuid;
    private final SharingScope scope;
    private final ConsentSignature consentSignature;
    
    private IntentToParticipate(String study, String email, String phone, String subpopGuid,
            SharingScope scope, ConsentSignature consentSignature) {
        this.study = study;
        this.email = email;
        this.phone = phone;
        this.subpopGuid = subpopGuid;
        this.scope = scope;
        this.consentSignature = consentSignature;
    }

    public String getStudy() {
        return study;
    }
    public String getEmail() {
        return email;
    }
    public String getPhone() {
        return phone;
    }
    public String getSubpopGuid() {
        return subpopGuid;
    }
    public SharingScope getScope() {
        return scope;
    }
    public ConsentSignature getConsentSignature() {
        return consentSignature;
    }
    
    public static class Builder {
        private String study;
        private String email;
        private String phone;
        private String subpopGuid;
        private SharingScope scope;
        private ConsentSignature consentSignature;
        
        public Builder withStudy(String study) {
            this.study = study;
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
        public Builder withSubpopGuid(String guid) {
            this.subpopGuid = guid;
            return this;
        }
        public Builder withScope(SharingScope scope) {
            this.scope = scope;
            return this;
        }
        public Builder withConsentSignature(ConsentSignature consentSignature) {
            this.consentSignature = consentSignature;
            return this;
        }
        public IntentToParticipate build() {
            return new IntentToParticipate(study, email, phone, subpopGuid, scope, consentSignature);
        }
    }
}
