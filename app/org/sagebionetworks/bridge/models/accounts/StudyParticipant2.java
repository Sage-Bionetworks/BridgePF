package org.sagebionetworks.bridge.models.accounts;

import static org.sagebionetworks.bridge.BridgeUtils.ifFailuresThrowException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * This object will replace the existing StudyParticipant object that is used to generate the 
 * participant roster (which is also going away).
 */
@BridgeTypeName("StudyParticipant")
@JsonDeserialize(builder=StudyParticipant2.Builder.class)
public class StudyParticipant2 {

    private final String firstName;
    private final String lastName;
    private final String email;
    private final String externalId;
    private final SharingScope sharingScope;
    private final boolean notifyByEmail;
    private final Set<String> dataGoups;
    private final String healthCode;
    private final Map<String,String> attributes;
    private final Map<String,List<UserConsentHistory>> consentHistories;
    private final Set<Roles> roles;
    private final LinkedHashSet<String> languages;
    
    private StudyParticipant2(String firstName, String lastName, String email, String externalId, SharingScope sharingScope,
            boolean notifyByEmail, Set<String> dataGroups, String healthCode, Map<String,String> attributes, 
            Map<String,List<UserConsentHistory>> consentHistories, Set<Roles> roles, LinkedHashSet<String> languages) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.externalId = externalId;
        this.sharingScope = sharingScope;
        this.notifyByEmail = notifyByEmail;
        this.dataGoups = dataGroups;
        this.healthCode = healthCode;
        this.attributes = attributes;
        this.consentHistories = consentHistories;
        this.roles = roles;
        this.languages = languages;
    }
    
    public String getFirstName() {
        return firstName;
    }
    public String getLastName() {
        return lastName;
    }
    public String getEmail() {
        return email;
    }
    public String getExternalId() {
        return externalId;
    }
    public SharingScope getSharingScope() {
        return sharingScope;
    }
    public boolean isNotifyByEmail() {
        return notifyByEmail;
    }
    public Set<String> getDataGroups() {
        return dataGoups;
    }
    public String getHealthCode() {
        return healthCode;
    }
    public Map<String,String> getAttributes() {
        return attributes;
    }
    public Map<String, List<UserConsentHistory>> getConsentHistories() {
        return consentHistories;
    }
    public Set<Roles> getRoles() {
        return roles;
    }
    public LinkedHashSet<String> getLanguages() {
        return languages;
    }
    
    public static class Builder {
        private String firstName;
        private String lastName;
        private String email;
        private String externalId;
        private SharingScope sharingScope;
        private boolean notifyByEmail;
        private Set<String> dataGroups = ImmutableSet.of();
        private String healthCode;
        private Map<String,String> attributes = Maps.newHashMap();
        private Map<String,List<UserConsentHistory>> consentHistories = Maps.newHashMap();
        private Set<Roles> roles = Sets.newHashSet();
        private LinkedHashSet<String> languages = new LinkedHashSet<>();
        
        public Builder withFirstName(String firstName) {
            this.firstName = firstName;
            return this;
        }
        public Builder withLastName(String lastName) {
            this.lastName = lastName;
            return this;
        }
        public Builder withEmail(String email) {
            this.email = email;
            return this;
        }
        public Builder withExternalId(String externalId) {
            this.externalId = externalId;
            return this;
        }
        public Builder withSharingScope(SharingScope sharingScope) {
            this.sharingScope = sharingScope;
            return this;
        }
        public Builder withNotifyByEmail(boolean notifyByEmail) {
            this.notifyByEmail = notifyByEmail;
            return this;
        }
        public Builder withDataGroups(Set<String> dataGroups) {
            if (dataGroups != null) {
                this.dataGroups = dataGroups;
            }
            return this;
        }
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withAttributes(Map<String,String> attributes) {
            if (attributes != null) {
                this.attributes = attributes;
            }
            return this;
        }
        public Builder addConsentHistory(SubpopulationGuid guid, List<UserConsentHistory> history) {
            if (guid != null && history != null) {
                this.consentHistories.put(guid.getGuid(), history);
            }
            return this;
        }
        public Builder withConsentHistories(Map<String,List<UserConsentHistory>> consentHistories) {
            if (consentHistories != null) {
                this.consentHistories = consentHistories;    
            }
            return this;
        }
        public Builder withRoles(Set<Roles> roles) {
            if (roles != null) {
                this.roles = roles;
            }
            return this;
        }
        public Builder withLanguages(LinkedHashSet<String> languages) {
            if (languages != null) {
                this.languages = languages;
            }
            return this;
        }
        
        public StudyParticipant2 build() {
            return new StudyParticipant2(firstName, lastName, email, externalId, sharingScope, notifyByEmail, 
                    dataGroups, healthCode, attributes, consentHistories, roles, languages);
        }
    }

}
