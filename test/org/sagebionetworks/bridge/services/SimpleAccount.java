package org.sagebionetworks.bridge.services;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class SimpleAccount implements Account {
    private String firstName;
    private String lastName;
    private String email;
    private String healthId;
    private StudyIdentifier studyId;
    private Map<SubpopulationGuid,List<ConsentSignature>> signatures = Maps.newHashMap();
    private Map<String,String> attributes = Maps.newHashMap();
    private Set<Roles> roles = Sets.newHashSet();
    private AccountStatus status;
    @Override
    public String getFirstName() {
        return firstName;
    }
    @Override
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    @Override
    public String getLastName() {
        return lastName;
    }
    @Override
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    @Override
    public String getAttribute(String name) {
        return attributes.get(name);
    }
    @Override
    public void setAttribute(String name, String value) {
        attributes.put(name, value);
    }
    @Override
    public String getEmail() {
        return email;
    }
    @Override
    public void setEmail(String email) {
        this.email = email;
    }
    @Override
    public List<ConsentSignature> getConsentSignatureHistory(SubpopulationGuid subpopGuid) {
        signatures.putIfAbsent(subpopGuid, Lists.newArrayList());
        return signatures.get(subpopGuid);
    }
    @Override
    public Map<SubpopulationGuid, List<ConsentSignature>> getAllConsentSignatureHistories() {
        return signatures;
    }
    @Override
    public String getHealthId() {
        return healthId;
    }
    @Override
    public void setHealthId(String healthId) {
        this.healthId = healthId;
    }
    @Override
    public StudyIdentifier getStudyIdentifier() {
        return studyId;
    }
    @Override
    public Set<Roles> getRoles() {
        return roles;
    }
    @Override
    public void setStatus(AccountStatus status) {
        this.status = status;
    }
    @Override
    public AccountStatus getStatus() {
        return status;
    }
    @Override
    public String getId() {
        return null;
    }
    @Override
    public DateTime getCreatedOn() {
        return DateTime.now();
    }
}
