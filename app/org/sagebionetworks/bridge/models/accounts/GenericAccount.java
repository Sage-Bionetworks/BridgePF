package org.sagebionetworks.bridge.models.accounts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

/**
 * Generic provider-agnostic implementation of Account, which contains data only as getters and setters with no
 * behaviors.
 */
// This class exists because the differences between what Hibernate needs and what the Stormpath implementation uses
// are just slightly different enough that it's not worth trying to pigeonhole two classes into one.
// See HibernateAccount for more discission.
@BridgeTypeName("Account")
public class GenericAccount implements Account {
    private final Map<String, String> attributeMap = new HashMap<>();
    private final Map<SubpopulationGuid, List<ConsentSignature>> consentHistoryMap = new HashMap<>();
    private DateTime createdOn;
    private String email;
    private String healthCode;
    private String healthId;
    private String id;
    private String firstName;
    private String lastName;
    private PasswordAlgorithm passwordAlgorithm;
    private String passwordHash;
    private String reauthToken;
    private Set<Roles> roleSet = ImmutableSet.of();
    private AccountStatus status;
    private StudyIdentifier studyId;
    private JsonNode clientData;
    private int version;

    /** {@inheritDoc} */
    @Override
    public String getFirstName() {
        return firstName;
    }

    /** {@inheritDoc} */
    @Override
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /** {@inheritDoc} */
    @Override
    public String getLastName() {
        return lastName;
    }

    /** {@inheritDoc} */
    @Override
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * The algorithm used to hash the password.
     *
     * @see PasswordAlgorithm
     */
    public PasswordAlgorithm getPasswordAlgorithm() {
        return passwordAlgorithm;
    }

    /** @see #getPasswordAlgorithm */
    public void setPasswordAlgorithm(PasswordAlgorithm passwordAlgorithm) {
        this.passwordAlgorithm = passwordAlgorithm;
    }

    /** The full password hash, as used by {@link PasswordAlgorithm} to decode it. */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** @see #getPasswordHash */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    /** The reauthentication token hash. */
    @Override
    public String getReauthToken() {
        return reauthToken;
    }

    /** @see #getReauthToken */
    @Override
    public void setReauthToken(String reauthToken) {
        this.reauthToken = reauthToken;
    }
    
    /** {@inheritDoc} */
    @Override
    public String getAttribute(String name) {
        return attributeMap.get(name);
    }

    /** {@inheritDoc} */
    @Override
    public void setAttribute(String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            attributeMap.put(name, value);
        } else {
            attributeMap.remove(name);
        }
    }

    /**
     * Set of attribute names that are current set on this account. Returns an empty set if there are no attributes.
     */
    public Set<String> getAttributeNameSet() {
        return attributeMap.keySet();
    }

    /** {@inheritDoc} */
    @Override
    public String getEmail() {
        return email;
    }

    /** {@inheritDoc} */
    @Override
    public void setEmail(String email) {
        this.email = email;
    }

    /** {@inheritDoc} */
    @Override
    public List<ConsentSignature> getConsentSignatureHistory(SubpopulationGuid subpopGuid) {
        List<ConsentSignature> consentList = consentHistoryMap.get(subpopGuid);
        if (consentList != null) {
            return consentList;
        } else {
            return ImmutableList.of();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setConsentSignatureHistory(SubpopulationGuid subpopGuid, List<ConsentSignature> consentSignatureList) {
        if (BridgeUtils.isEmpty(consentSignatureList)) {
            consentHistoryMap.remove(subpopGuid);
        } else {
            consentHistoryMap.put(subpopGuid, ImmutableList.copyOf(consentSignatureList));
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<SubpopulationGuid, List<ConsentSignature>> getAllConsentSignatureHistories() {
        return ImmutableMap.copyOf(consentHistoryMap);
    }

    /** {@inheritDoc} */
    @Override
    public String getHealthCode() {
        return healthCode;
    }

    /** @see #getHealthCode */
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** Account health ID, which maps to health code. */
    public String getHealthId() {
        return healthId;
    }

    /** @see #getHealthId */
    public void setHealthId(String healthId) {
        this.healthId = healthId;
    }

    /** {@inheritDoc} */
    @Override
    public void setHealthId(HealthId healthId) {
        this.healthCode = healthId.getCode();
        this.healthId = healthId.getId();
    }

    /** {@inheritDoc} */
    @Override
    public AccountStatus getStatus() {
        return status;
    }

    /** {@inheritDoc} */
    @Override
    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    /** {@inheritDoc} */
    @Override
    public StudyIdentifier getStudyIdentifier() {
        return studyId;
    }

    /** @see #getStudyIdentifier */
    public void setStudyId(StudyIdentifier studyId) {
        this.studyId = studyId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<Roles> getRoles() {
        return roleSet;
    }

    /** {@inheritDoc} */
    @Override
    public void setRoles(Set<Roles> roles) {
        this.roleSet = !BridgeUtils.isEmpty(roles) ? ImmutableSet.copyOf(roles) : ImmutableSet.of();
    }

    /** {@inheritDoc} */
    @Override
    public String getId() {
        return id;
    }

    /** @see #getId */
    public void setId(String id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @Override
    public DateTime getCreatedOn() {
        return createdOn;
    }

    /** @see #getCreatedOn */
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    /** {@inheritDoc} */
    @Override
    public JsonNode getClientData() {
        return clientData;
    }

    /** @see #getCreatedOn */
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
    }

    /** Version number, used by Hibernate to handle optimistic locking. */
    public int getVersion() {
        return version;
    }

    /** @see #getVersion */
    public void setVersion(int version) {
        this.version = version;
    }
}
