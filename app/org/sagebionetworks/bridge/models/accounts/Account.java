package org.sagebionetworks.bridge.models.accounts;

import static java.util.Comparator.comparing;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.hibernate.HibernateAccount;
import org.sagebionetworks.bridge.hibernate.HibernateAccountConsent;
import org.sagebionetworks.bridge.hibernate.HibernateAccountConsentKey;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Encryption of account values is handled transparently by the account implementation. 
 * All values are set and retrieved in clear text.
 */
public interface Account extends BridgeEntity {

    static Account create() {
        return new HibernateAccount();
    }
    
    default ConsentSignature getActiveConsentSignature(SubpopulationGuid subpopGuid) {
        List<ConsentSignature> history = getConsentSignatureHistory(subpopGuid);
        if (!history.isEmpty()) {
            ConsentSignature signature = history.get(history.size()-1);
            return (signature.getWithdrewOn() == null) ? signature : null;
        }
        return null;
    }
    
    /**
     * Gets an immutable copy of the consents this account has for the given subpopulation. Consents should be returned
     * in the order they were signed (earliest first). Returns an empty list if there are no consents.
     */
    default List<ConsentSignature> getConsentSignatureHistory(SubpopulationGuid subpopGuid) {
        List<ConsentSignature> signatures = Lists.newArrayList();
        for (Map.Entry<HibernateAccountConsentKey, HibernateAccountConsent> entry : getConsents().entrySet()) {
            HibernateAccountConsentKey key = entry.getKey();
            HibernateAccountConsent consent = entry.getValue();
            
            if (key.getSubpopulationGuid().equals(subpopGuid.getGuid())) {
                ConsentSignature signature = new ConsentSignature.Builder()
                    .withName(consent.getName())
                    .withBirthdate(consent.getBirthdate())
                    .withImageData(consent.getSignatureImageData())
                    .withImageMimeType(consent.getSignatureImageMimeType())
                    .withConsentCreatedOn(consent.getConsentCreatedOn())
                    .withSignedOn(key.getSignedOn())
                    .withWithdrewOn(consent.getWithdrewOn()).build();
                signatures.add(signature);
            }
        }
        signatures.sort(comparing(ConsentSignature::getSignedOn));
        return ImmutableList.copyOf(signatures);
    }

    /**
     * Sets the consents for the given subpopulation into the account. The consents should be in the order they were
     * signed (earliest first). A copy of the consent list is mode, so that changes to the input list does not
     * propagate to the account.
     */
    default void setConsentSignatureHistory(SubpopulationGuid subpopGuid, List<ConsentSignature> consentSignatureList) {
        for (ConsentSignature signature : consentSignatureList) {
            HibernateAccountConsentKey key = new HibernateAccountConsentKey(subpopGuid.getGuid(), signature.getSignedOn());
            HibernateAccountConsent consent = new HibernateAccountConsent();
            consent.setBirthdate(signature.getBirthdate());
            consent.setConsentCreatedOn(signature.getConsentCreatedOn());
            consent.setName(signature.getName());
            consent.setSignatureImageData(signature.getImageData());
            consent.setSignatureImageMimeType(signature.getImageMimeType());
            consent.setWithdrewOn(signature.getWithdrewOn());
            getConsents().put(key, consent);
        }
    }

    /** Returns an immutable copy of all consents in the account, keyed by subpopulation. */
    default Map<SubpopulationGuid, List<ConsentSignature>> getAllConsentSignatureHistories() {
        Map<SubpopulationGuid, List<ConsentSignature>> map = Maps.newHashMap();
        
        for (Map.Entry<HibernateAccountConsentKey, HibernateAccountConsent> entry : getConsents().entrySet()) {
            HibernateAccountConsentKey key = entry.getKey();
            HibernateAccountConsent consent = entry.getValue();
            
            SubpopulationGuid guid = SubpopulationGuid.create(key.getSubpopulationGuid());
            
            List<ConsentSignature> signatures = map.get(guid);
            if (signatures == null) {
                signatures = Lists.newArrayList();
                map.put(guid, signatures);
            }
            ConsentSignature signature = new ConsentSignature.Builder()
                    .withName(consent.getName())
                    .withBirthdate(consent.getBirthdate())
                    .withImageData(consent.getSignatureImageData())
                    .withImageMimeType(consent.getSignatureImageMimeType())
                    .withConsentCreatedOn(consent.getConsentCreatedOn())
                    .withSignedOn(key.getSignedOn())
                    .withWithdrewOn(consent.getWithdrewOn()).build();
            signatures.add(signature);
        }
        map.values().forEach(list -> list.sort(comparing(ConsentSignature::getSignedOn)));
        return ImmutableMap.copyOf(map);
    }
    
    Map<HibernateAccountConsentKey, HibernateAccountConsent> getConsents();
    void setConsents(Map<HibernateAccountConsentKey, HibernateAccountConsent> consents);
    
    String getFirstName();
    void setFirstName(String firstName);
    
    String getLastName();
    void setLastName(String lastName);
    
    Map<String,String> getAttributes();
    void setAttributes(Map<String,String> attributes);

    String getEmail();
    void setEmail(String email);
    
    Phone getPhone();
    void setPhone(Phone phone);
    
    Boolean getEmailVerified();
    void setEmailVerified(Boolean emailVerified);
    
    Boolean getPhoneVerified();
    void setPhoneVerified(Boolean phoneVerified);
    
    String getReauthToken();
    void setReauthToken(String reauthToken);

    String getHealthCode();
    void setHealthCode(String healthCode);

    AccountStatus getStatus();
    void setStatus(AccountStatus status);

    String getStudyId();
    void setStudyId(String studyId);

    /** Gets an immutable copy of the set of roles attached to this account. */
    Set<Roles> getRoles();

    /**
     * Sets the roles in this account to match the roles specified by the input. A copy of the roles is made, so that
     * changes to the input list does not propagate to the account.
     */
    void setRoles(Set<Roles> roles);

    /**
     * Unique identifier for the account. This is used so that we have an opaque identifier that's not the user's email
     * address or a user-chosen (possibly identifying) username.
     */
    String getId();
    void setId(String id);

    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);
    
    DateTime getModifiedOn();
    void setModifiedOn(DateTime modifiedOn);
    
    /**
     * Arbitrary JsonNode data that can be persisted by the client application for a specific user. Like other account data, 
     * this can include PII, and can be up to 16MB when serialized (however, we expect data of 1MB or less to be normal usage). 
     */
    JsonNode getClientData();
    void setClientData(JsonNode clientData);
    
    /** The time zone that has been captured for the user. */
    DateTimeZone getTimeZone();
    void setTimeZone(DateTimeZone timeZone);

    /** The sharing scope that currently applies for this user. */
    SharingScope getSharingScope();
    void setSharingScope(SharingScope sharingScope);
    
    /** Has the user consented to receive email about the study from the study administrators? */
    Boolean getNotifyByEmail();
    void setNotifyByEmail(Boolean notifyByEmail);
    
    /** The external ID that has been assigned to this account. */
    String getExternalId();
    void setExternalId(String externalId);
    
    /** The data groups assigned to this account. */
    Set<String> getDataGroups();
    void setDataGroups(Set<String> dataGroups);
    
    /** The languages that have been captured from HTTP requests for this account. */
    List<String> getLanguages();
    void setLanguages(List<String> languages);
    
    /** A flag used to track changes in the contents of the table across migrations. */
    int getMigrationVersion();
    void setMigrationVersion(int migrationVersion);
    
    int getVersion();
    void setVersion(int version);

    String getPasswordHash();
    void setPasswordHash(String passwordHash);
    
    DateTime getPasswordModifiedOn();
    void setPasswordModifiedOn(DateTime passwordModifiedOn);
    
    PasswordAlgorithm getPasswordAlgorithm();
    void setPasswordAlgorithm(PasswordAlgorithm passwordAlgorithm);
    
    PasswordAlgorithm getReauthTokenAlgorithm();
    void setReauthTokenAlgorithm(PasswordAlgorithm reauthTokenAlgorithm);
    
    String getReauthTokenHash();
    void setReauthTokenHash(String reauthTokenHash);
    
    DateTime getReauthTokenModifiedOn();
    void setReauthTokenModifiedOn(DateTime reauthTokenModifiedOn);
    
    void setAccountSubstudies(Set<AccountSubstudy> accountSubstudies);
    Set<AccountSubstudy> getAccountSubstudies();
}
