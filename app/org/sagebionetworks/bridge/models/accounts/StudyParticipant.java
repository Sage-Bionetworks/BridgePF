package org.sagebionetworks.bridge.models.accounts;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * This object represents a participant in the system.
 */
@JsonDeserialize(builder=StudyParticipant.Builder.class)
@JsonFilter("filter")
public final class StudyParticipant implements BridgeEntity {

    /** Serialize study participant to include the encryptedHealthCode but not healthCode. */
    public static final ObjectWriter CACHE_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter", 
            SimpleBeanPropertyFilter.serializeAllExcept("healthCode")));

    /** Serialize the study participant including healthCode and excluding encryptedHealthCode. */
    public static final ObjectWriter API_WITH_HEALTH_CODE_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter",
            SimpleBeanPropertyFilter.serializeAllExcept("encryptedHealthCode")));
    
    /** Serialize the study participant with neither healthCode nor encryptedHealthCode. */
    public static final ObjectWriter API_NO_HEALTH_CODE_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter",
            SimpleBeanPropertyFilter.serializeAllExcept("healthCode", "encryptedHealthCode")));
    
    private static final Encryptor ENCRYPTOR = new AesGcmEncryptor(
            BridgeConfigFactory.getConfig().getProperty("bridge.healthcode.redis.key"));
    
    private final String firstName;
    private final String lastName;
    private final String email;
    private final Phone phone;
    private final Boolean emailVerified;
    private final Boolean phoneVerified;
    private final String externalId;
    private final String password;
    private final SharingScope sharingScope;
    private final Boolean notifyByEmail;
    private final Set<String> dataGroups;
    private final String healthCode;
    private final Map<String,String> attributes;
    private final Map<String,List<UserConsentHistory>> consentHistories;
    private final Boolean consented;
    private final Set<Roles> roles;
    private final List<String> languages;
    private final AccountStatus status;
    private final DateTime createdOn;
    private final String id;
    private final DateTimeZone timeZone;
    private final JsonNode clientData;
    private final Set<String> substudyIds;
    private final Map<String,String> externalIds;
    
    private StudyParticipant(String firstName, String lastName, String email, Phone phone, Boolean emailVerified,
            Boolean phoneVerified, String externalId, String password, SharingScope sharingScope, Boolean notifyByEmail,
            Set<String> dataGroups, String healthCode, Map<String, String> attributes,
            Map<String, List<UserConsentHistory>> consentHistories, Boolean consented, Set<Roles> roles,
            List<String> languages, AccountStatus status, DateTime createdOn, String id, DateTimeZone timeZone,
            JsonNode clientData, Set<String> substudyIds, Map<String,String> externalIds) {
        
        ImmutableMap.Builder<String, List<UserConsentHistory>> immutableConsentsBuilder = new ImmutableMap.Builder<>();
        if (consentHistories != null) {
            for (Map.Entry<String, List<UserConsentHistory>> entry : consentHistories.entrySet()) {
                if (entry.getValue() != null) {
                    List<UserConsentHistory> immutableList = BridgeUtils.nullSafeImmutableList(entry.getValue());
                    immutableConsentsBuilder.put(entry.getKey(), immutableList);
                }
            }
        }
        
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.externalId = externalId;
        this.password = password;
        this.sharingScope = sharingScope;
        this.notifyByEmail = notifyByEmail;
        this.dataGroups = BridgeUtils.nullSafeImmutableSet(dataGroups);
        this.healthCode = healthCode;
        this.attributes = BridgeUtils.nullSafeImmutableMap(attributes);
        this.consentHistories = immutableConsentsBuilder.build();
        this.consented = consented;
        this.roles = BridgeUtils.nullSafeImmutableSet(roles);
        this.languages = BridgeUtils.nullSafeImmutableList(languages);
        this.status = status;
        this.createdOn = createdOn;
        this.id = id;
        this.timeZone = timeZone;
        this.clientData = clientData;
        this.substudyIds = BridgeUtils.nullSafeImmutableSet(substudyIds);
        this.externalIds = BridgeUtils.nullSafeImmutableMap(externalIds);
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
    public Phone getPhone() {
        return phone;
    }
    public Boolean getEmailVerified() {
        return emailVerified;
    }
    public Boolean getPhoneVerified() {
        return phoneVerified;
    }
    public String getExternalId() {
        return externalId;
    }
    public String getPassword() {
        return password;
    }
    public SharingScope getSharingScope() {
        return sharingScope;
    }
    public Boolean isNotifyByEmail() {
        return notifyByEmail;
    }
    public Set<String> getDataGroups() {
        return dataGroups;
    }
    public String getHealthCode() {
        return healthCode;
    }
    public String getEncryptedHealthCode() {
        return (healthCode == null) ? null : ENCRYPTOR.encrypt(healthCode);
    }
    public Map<String,String> getAttributes() {
        return attributes;
    }
    public Map<String, List<UserConsentHistory>> getConsentHistories() {
        return consentHistories;
    }

    /**
     * True if the user has consented to all required consents, based on the user's most recent request info (client
     * info, languages, data groups). May be null if this object was not constructed with consent histories, or if
     * consent status is indeterminate.
     */
    public Boolean isConsented() {
        return consented;
    }

    public Set<Roles> getRoles() {
        return roles;
    }
    public List<String> getLanguages() {
        return languages;
    }
    public AccountStatus getStatus() {
        return status;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public String getId() {
        return id;
    }
    public DateTimeZone getTimeZone() {
        return timeZone;
    }
    public JsonNode getClientData() {
        return clientData;
    }
    public Set<String> getSubstudyIds() {
        return substudyIds;
    }
    public Map<String,String> getExternalIds(){ 
        return externalIds;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(attributes, consentHistories, consented, createdOn, dataGroups, email, phone, emailVerified,
                phoneVerified, externalId, firstName, healthCode, id, languages, lastName, notifyByEmail, password,
                roles, sharingScope, status, timeZone, clientData, substudyIds, externalIds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        StudyParticipant other = (StudyParticipant) obj;
        return Objects.equals(attributes, other.attributes) && Objects.equals(consentHistories, other.consentHistories)
                && Objects.equals(consented, other.consented)
                && Objects.equals(createdOn, other.createdOn) && Objects.equals(dataGroups, other.dataGroups)
                && Objects.equals(email, other.email) && Objects.equals(phone, other.phone)
                && Objects.equals(emailVerified, other.emailVerified) && Objects.equals(phoneVerified, other.phoneVerified)
                && Objects.equals(externalId, other.externalId)
                && Objects.equals(firstName, other.firstName) && Objects.equals(healthCode, other.healthCode)
                && Objects.equals(id, other.id) && Objects.equals(languages, other.languages)
                && Objects.equals(lastName, other.lastName) && Objects.equals(notifyByEmail, other.notifyByEmail)
                && Objects.equals(password, other.password) && Objects.equals(roles, other.roles)
                && Objects.equals(sharingScope, other.sharingScope) && Objects.equals(status, other.status)
                && Objects.equals(timeZone, other.timeZone)
                && Objects.equals(clientData, other.clientData)
                && Objects.equals(substudyIds, other.substudyIds)
                && Objects.equals(externalIds, other.externalIds);
    }

    public static class Builder {
        private String firstName;
        private String lastName;
        private String email;
        private Phone phone;
        private Boolean emailVerified;
        private Boolean phoneVerified;
        private String externalId;
        private String password;
        private SharingScope sharingScope;
        private Boolean notifyByEmail;
        private Set<String> dataGroups;
        private String healthCode;
        private Map<String,String> attributes;
        private Map<String,List<UserConsentHistory>> consentHistories;
        private Boolean consented;
        private Set<Roles> roles;
        private List<String> languages;
        private AccountStatus status;
        private DateTime createdOn;
        private String id;
        private DateTimeZone timeZone;
        private JsonNode clientData;
        private Set<String> substudyIds;
        private Map<String,String> externalIds;
        
        public Builder copyOf(StudyParticipant participant) {
            this.firstName = participant.getFirstName();
            this.lastName = participant.getLastName();
            this.email = participant.getEmail();
            this.phone = participant.getPhone();
            this.emailVerified = participant.getEmailVerified();
            this.phoneVerified = participant.getPhoneVerified();
            this.externalId = participant.getExternalId();
            this.password = participant.getPassword();
            this.sharingScope = participant.getSharingScope();
            this.notifyByEmail = participant.isNotifyByEmail();
            this.healthCode = participant.getHealthCode();
            this.dataGroups = participant.getDataGroups();
            this.attributes = participant.getAttributes();
            this.consentHistories = participant.getConsentHistories();
            this.consented = participant.isConsented();
            this.roles = participant.getRoles();
            this.languages = participant.getLanguages();
            this.status = participant.getStatus();
            this.createdOn = participant.getCreatedOn();
            this.id = participant.getId();
            this.timeZone = participant.getTimeZone();
            this.clientData = participant.getClientData();
            this.substudyIds = participant.getSubstudyIds();
            this.externalIds = participant.getExternalIds();
            return this;
        }
        public Builder copyFieldsOf(StudyParticipant participant, Set<String> fieldNames) {
            if (fieldNames.contains("firstName")) {
                withFirstName(participant.getFirstName());    
            }
            if (fieldNames.contains("lastName")) {
                withLastName(participant.getLastName());    
            }
            if (fieldNames.contains("email")) {
                withEmail(participant.getEmail());
            }
            if (fieldNames.contains("phone")) {
                withPhone(participant.getPhone());
            }
            if (fieldNames.contains("emailVerified")) {
                withEmailVerified(participant.getEmailVerified());
            }
            if (fieldNames.contains("phoneVerified")) {
                withPhoneVerified(participant.getPhoneVerified());
            }
            if (fieldNames.contains("externalId")) {
                withExternalId(participant.getExternalId());    
            }
            if (fieldNames.contains("password")) {
                withPassword(participant.getPassword());    
            }
            if (fieldNames.contains("sharingScope")) {
                withSharingScope(participant.getSharingScope());
            }
            if (fieldNames.contains("notifyByEmail")) {
                withNotifyByEmail(participant.isNotifyByEmail());    
            }
            if (fieldNames.contains("healthCode")) {
                withHealthCode(participant.getHealthCode());    
            }
            if (fieldNames.contains("dataGroups")) {
                withDataGroups(participant.getDataGroups());    
            }
            if (fieldNames.contains("attributes")) {
                withAttributes(participant.getAttributes());    
            }
            if (fieldNames.contains("consentHistories")) {
                withConsentHistories(participant.getConsentHistories());    
            }
            if (fieldNames.contains("consented")) {
                withConsented(participant.isConsented());
            }
            if (fieldNames.contains("roles")) {
                withRoles(participant.getRoles());    
            }
            if (fieldNames.contains("languages")) {
                withLanguages(participant.getLanguages());    
            }
            if (fieldNames.contains("status")){
                withStatus(participant.getStatus());    
            }
            if (fieldNames.contains("createdOn")) {
                withCreatedOn(participant.getCreatedOn());    
            }
            if (fieldNames.contains("id")) {
                withId(participant.getId());    
            }
            if (fieldNames.contains("timeZone")) {
                withTimeZone(participant.getTimeZone());    
            }
            if (fieldNames.contains("clientData")) {
                withClientData(participant.getClientData());
            }
            if (fieldNames.contains("substudyIds")) {
                withSubstudyIds(participant.getSubstudyIds());
            }
            if (fieldNames.contains("externalIds")) {
                withExternalIds(participant.getExternalIds());
            }
            return this;
        }
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
        public Builder withPhone(Phone phone) {
            this.phone = phone;
            return this;
        }
        public Builder withEmailVerified(Boolean emailVerified) {
            this.emailVerified = emailVerified;
            return this;
        }
        public Builder withPhoneVerified(Boolean phoneVerified) {
            this.phoneVerified = phoneVerified;
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
        public Builder withSharingScope(SharingScope sharingScope) {
            this.sharingScope = sharingScope;
            return this;
        }
        public Builder withNotifyByEmail(Boolean notifyByEmail) {
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
        public Builder withEncryptedHealthCode(String encHealthCode) {
            withHealthCode((encHealthCode == null) ? null : ENCRYPTOR.decrypt(encHealthCode));
            return this;
        }
        public Builder withAttributes(Map<String,String> attributes) {
            if (attributes != null) {
                this.attributes = attributes;
            }
            return this;
        }
        public Builder withConsentHistories(Map<String,List<UserConsentHistory>> consentHistories) {
            if (consentHistories != null) {
                this.consentHistories = consentHistories;    
            }
            return this;
        }
        public Builder withConsented(Boolean consented) {
            this.consented = consented;
            return this;
        }
        public Builder withRoles(Set<Roles> roles) {
            if (roles != null) {
                this.roles = roles;
            }
            return this;
        }
        public Builder withLanguages(List<String> languages) {
            if (languages != null) {
                this.languages = languages;
            }
            return this;
        }
        public Builder withStatus(AccountStatus status) {
            this.status = status;
            return this;
        }
        public Builder withCreatedOn(DateTime createdOn) {
            this.createdOn = createdOn;
            return this;
        }
        public Builder withId(String id) {
            this.id = id;
            return this;
        }
        public Builder withTimeZone(DateTimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }
        public Builder withClientData(JsonNode clientData) {
            this.clientData = clientData;
            return this;
        }
        public Builder withSubstudyIds(Set<String> substudyIds) {
            this.substudyIds = substudyIds;
            return this;
        }
        public Builder withExternalIds(Map<String,String> externalIds) {
            this.externalIds = externalIds;
            return this;
        }
        public StudyParticipant build() {
            Boolean emailVerified = this.emailVerified;
            if (emailVerified == null) {
                if (status == AccountStatus.ENABLED) {
                    emailVerified = Boolean.TRUE;
                } else if (status == AccountStatus.UNVERIFIED) {
                    emailVerified = Boolean.FALSE;
                }
            }
            // deduplicate language codes if they have been doubled
            if (languages != null) {
                languages = ImmutableList.copyOf(Sets.newLinkedHashSet(languages));
            }
            return new StudyParticipant(firstName, lastName, email, phone, emailVerified, phoneVerified, externalId,
                    password, sharingScope, notifyByEmail, dataGroups, healthCode, attributes, consentHistories, consented, roles,
                    languages, status, createdOn, id, timeZone, clientData, substudyIds, externalIds);
        }
    }

}
