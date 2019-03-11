package org.sagebionetworks.bridge.models.itp;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = IntentToParticipate.Builder.class)
public class IntentToParticipate implements BridgeEntity {
    private final String studyId;
    private final Phone phone;
    private final String email;
    private final String subpopGuid;
    private final SharingScope scope;
    private final String osName;
    private final ConsentSignature consentSignature;
    
    private IntentToParticipate(String studyId, Phone phone, String email, String subpopGuid, SharingScope scope,
            String osName, ConsentSignature consentSignature) {
        this.studyId = studyId;
        this.phone = phone;
        this.email = email;
        this.subpopGuid = subpopGuid;
        this.scope = scope;
        this.osName = osName;
        this.consentSignature = consentSignature;
    }

    public String getStudyId() {
        return studyId;
    }
    public Phone getPhone() {
        return phone;
    }
    public String getEmail() {
        return email;
    }
    public String getSubpopGuid() {
        return subpopGuid;
    }
    public SharingScope getScope() {
        return scope;
    }
    public String getOsName() {
        return osName;
    }
    public ConsentSignature getConsentSignature() {
        return consentSignature;
    }
    
    public static class Builder {
        private String studyId;
        private Phone phone;
        private String email;
        private String subpopGuid;
        private SharingScope scope;
        private String osName;
        private ConsentSignature consentSignature;
        
        public Builder copyOf(IntentToParticipate intent) {
            this.studyId = intent.studyId;
            this.phone = intent.phone;
            this.email = intent.email;
            this.subpopGuid = intent.subpopGuid;
            this.scope = intent.scope;
            this.osName = intent.osName;
            this.consentSignature = intent.consentSignature;
            return this;
        }
        public Builder withStudyId(String studyId) {
            this.studyId = studyId;
            return this;
        }
        public Builder withPhone(Phone phone) {
            this.phone = phone;
            return this;
        }
        public Builder withEmail(String email) {
            this.email = email;
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
        public Builder withOsName(String osName) {
            this.osName = osName;
            return this;
        }
        public Builder withConsentSignature(ConsentSignature consentSignature) {
            this.consentSignature = consentSignature;
            return this;
        }
        public IntentToParticipate build() {
            // Same adjustments that we do for ClientInfo, to normalize the name if it changes.
            if (OperatingSystem.SYNONYMS.containsKey(osName)) {
                osName = OperatingSystem.SYNONYMS.get(osName);
            }
            return new IntentToParticipate(studyId, phone, email, subpopGuid, scope, osName, consentSignature);
        }
    }
}
