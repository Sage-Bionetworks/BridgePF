package org.sagebionetworks.bridge.models.accounts;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

@BridgeTypeName("User")
public final class User implements BridgeEntity {

    private static final Encryptor encryptor = new AesGcmEncryptor(
            BridgeConfigFactory.getConfig().getProperty("bridge.healthcode.redis.key"));

    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String healthCode;
    private String studyKey;
    private SharingScope sharingScope;
    private Set<Roles> roles = Sets.newHashSet();
    private Set<String> dataGroups = Sets.newHashSet();
    private Map<SubpopulationGuid,ConsentStatus> consentStatuses;

    public User() {
        this.consentStatuses = ImmutableMap.of();
    }

    public User(Account account) {
        this();
        this.email = account.getEmail();
        this.username = account.getUsername();
        this.firstName = account.getFirstName();
        this.lastName = account.getLastName();
        this.id = account.getId();
        this.roles = new HashSet<>(account.getRoles());
    }

    public User(String id, String email) {
        setId(id);
        setEmail(email);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @JsonIgnore
    public String getHealthCode() {
        return healthCode;
    }

    @JsonIgnore
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    public String getEncryptedHealthCode() {
        return encryptor.encrypt(healthCode);
    }

    public void setEncryptedHealthCode(String healthCode) {
        this.healthCode = encryptor.decrypt(healthCode);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStudyKey() {
        return studyKey;
    }

    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }

    public Set<Roles> getRoles() {
        return roles;
    }

    public void setRoles(Set<Roles> roles) {
        this.roles = BridgeUtils.nullSafeImmutableSet(roles);
    }
    
    public Set<String> getDataGroups() {
        return dataGroups;
    }
    
    public void setDataGroups(Set<String> dataGroups) {
        this.dataGroups = BridgeUtils.nullSafeImmutableSet(dataGroups);
    }

    public SharingScope getSharingScope() {
        return sharingScope;
    }

    public void setSharingScope(SharingScope sharingScope) {
        this.sharingScope = sharingScope;
    }

    public boolean isInRole(Roles role) {
        return (role != null && this.roles.contains(role));
    }
    
    public boolean isInRole(Set<Roles> roleSet) {
        return roleSet != null && !Collections.disjoint(roles, roleSet);
    }
    
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses() {
        return consentStatuses;
    }
    
    public void setConsentStatuses(Map<SubpopulationGuid,ConsentStatus> consentStatuses) {
        this.consentStatuses = (consentStatuses == null) ? ImmutableMap.of() : ImmutableMap.copyOf(consentStatuses);
    }

    /**
     * Has the user consented to all required consents they are eligible for?
     * @return
     */
    public boolean doesConsent() {
        return ConsentStatus.isUserConsented(consentStatuses.values());
    }

    /**
     * Are all the required consents up-to-date?
     * @return
     */
    public boolean hasSignedMostRecentConsent() {
        return ConsentStatus.isConsentCurrent(consentStatuses.values());
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(email, firstName, lastName, healthCode, id, roles, sharingScope, studyKey, username,
                dataGroups, consentStatuses);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        User other = (User) obj;
        return (Objects.equal(email, other.email) && Objects.equal(firstName, other.firstName)
                && Objects.equal(lastName, other.lastName) && Objects.equal(healthCode, other.healthCode)
                && Objects.equal(id, other.id) && Objects.equal(roles, other.roles)
                && Objects.equal(sharingScope, other.sharingScope) && Objects.equal(studyKey, other.studyKey)
                && Objects.equal(username, other.username) && Objects.equal(dataGroups, other.dataGroups)
                && Objects.equal(consentStatuses, other.consentStatuses));
    }

    @Override
    public String toString() {
        return String.format("User [email=%s, firstName=%s, lastName=%s, id=%s, roles=%s, sharingScope=%s, studyKey=%s, username=%s, dataGroups=%s, consentStatuses=%s]", 
                email, firstName, lastName, id, roles, sharingScope, studyKey, username, dataGroups, consentStatuses);
    }
}
