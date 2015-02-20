package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Set;
import java.util.SortedMap;

import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.stormpath.sdk.group.Group;

/**
 * Account values are decrypted with the appropriate Encryptor implementation based on the version 
 * stored with the encrypted value; values are always encrypted with the most recent Encryptor 
 * implementation. To migrate to the latest encryption implementation, you can simply call (using 
 * the example of the phone number) <code>account.setPhone(account.getPhone())</code>, and then 
 * save the account via the AccountDao.
 * 
 * There are historical exceptions including the "<studyId>version" key for the health ID and 
 * no version for the phone value; these are handled in the current implementation. Again on 
 * updating these values, the version keys will be updated.
 */
@BridgeTypeName("Account")
class StormpathAccount implements Account {
    
    // private static Logger logger = LoggerFactory.getLogger(StormpathAccount.class);
    
    static final String PLACEHOLDER_STRING = "<EMPTY>";
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PHONE_ATTRIBUTE = "phone";
    public static final String HEALTH_CODE_SUFFIX = "_code";
    public static final String CONSENT_SIGNATURE_SUFFIX = "_consent_signature";
    public static final String VERSION_SUFFIX = "_version";
    public static final String OLD_VERSION_SUFFIX = "version";
    
    private final com.stormpath.sdk.account.Account acct;
    private final SortedMap<Integer,Encryptor> encryptors;
    private final String healthIdKey;
    private final String consentSignatureKey;
    private final String oldHealthIdVersionKey;
    private final Set<String> roles;
    
    StormpathAccount(StudyIdentifier studyIdentifier, com.stormpath.sdk.account.Account acct,
            SortedMap<Integer, Encryptor> encryptors) {
        checkNotNull(studyIdentifier);
        checkNotNull(acct);
        checkNotNull(encryptors);
        
        String studyId = studyIdentifier.getIdentifier();
        
        this.acct = acct;
        this.encryptors = encryptors;
        this.healthIdKey = studyId + HEALTH_CODE_SUFFIX;
        this.consentSignatureKey = studyId + CONSENT_SIGNATURE_SUFFIX;
        this.oldHealthIdVersionKey = studyId + OLD_VERSION_SUFFIX;
        this.roles = Sets.newHashSet();
        if (acct.getGroups() != null) {
            for (Group group : acct.getGroups()) {
                this.roles.add(group.getName());
            }
        }
    }
    
    com.stormpath.sdk.account.Account getAccount() {
        return acct;
    }
    
    @Override
    public String getUsername() {
        return acct.getUsername();
    }
    @Override
    public void setUsername(String username) {
        acct.setUsername(username);
    }
    @Override
    public String getFirstName() {
        String firstName = acct.getGivenName();
        return (PLACEHOLDER_STRING.equals(firstName)) ? null : firstName;
    }
    @Override
    public void setFirstName(String firstName) {
        acct.setGivenName(firstName);
    }
    @Override
    public String getLastName() {
        String lastName = acct.getSurname();
        return (PLACEHOLDER_STRING.equals(lastName)) ? null : lastName;
    }
    @Override
    public void setLastName(String lastName) {
        acct.setSurname(lastName);
    }
    @Override
    public String getPhone() {
        return decryptFrom(PHONE_ATTRIBUTE);
    }
    @Override
    public void setPhone(String phone) {
        encryptTo(PHONE_ATTRIBUTE, phone);
    }
    @Override
    public String getEmail() {
        return acct.getEmail();
    }
    @Override
    public void setEmail(String email) {
        acct.setEmail(email);
    }
    @Override
    public String getHealthId(){
        return decryptFrom(healthIdKey);
    }
    @Override
    public void setHealthId(String healthId) {
        encryptTo(healthIdKey, healthId);
    };
    @Override
    public ConsentSignature getConsentSignature() {
        return decryptJSONFrom(consentSignatureKey, ConsentSignature.class);
    }
    @Override
    public void setConsentSignature(ConsentSignature signature) {
        encryptJSONTo(consentSignatureKey, signature);
    }
    @Override
    public Set<String> getRoles() {
        return this.roles;
    }
    @Override
    public String getId() {
        return acct.getHref().split("/accounts/")[1];
    }
    
    /* May use to encrypt first/last name, though these could just be stored in customData as well
    private Object[] encryptFieldValue(String field, String value) {
        if (value == null) {
            return new Object[] {null, value};
        }
        Encryptor encryptor = encryptors.get(encryptors.lastKey());
        return new Object[] {encryptors.lastKey(), encryptor.encrypt(value)};
    }
    private String decryptFieldValue(String field, String encryptedValue) {
        Integer version = (Integer)acct.getCustomData().get(field+VERSION_SUFFIX);
        if (version == null) {
            return encryptedValue;
        }
        Encryptor encryptor = encryptors.get(version);
        return (encryptor == null) ? encryptedValue : encryptor.decrypt(encryptedValue);
    }
    */

    private void encryptJSONTo(String key, Object value) {
        if (value == null) {
            acct.getCustomData().remove(key);
            return;
        }
        try {
            String jsonString = MAPPER.writeValueAsString(value);
            encryptTo(key, jsonString);
        } catch(JsonProcessingException e) {
            String message = String.format("Could not store %s due to malformed JSON: %s", key, e.getMessage());
            throw new BridgeServiceException(message);
        }
    }
    
    private <T> T decryptJSONFrom(String key, Class<T> clazz) {
        try {
            String jsonString = decryptFrom(key);
            if (jsonString == null) {
                return null;
            }
            return MAPPER.readValue(jsonString, clazz);
        } catch(IOException e) {
            String message = String.format("Could not retrieve %s due to malformed JSON: %s", key, e.getMessage());
            throw new BridgeServiceException(message);
        }
    }
    
    private void encryptTo(String key, String value) {
        if (value == null) {
            acct.getCustomData().remove(key);
            return;
        }
        // Encryption is always done with the most recent encryptor, which is last in the list (most revent version #)
        Integer encryptorKey = encryptors.lastKey();
        Encryptor encryptor = encryptors.get(encryptorKey);

        String encrypted = encryptor.encrypt(value);
        acct.getCustomData().put(key, encrypted);
        acct.getCustomData().put(key+VERSION_SUFFIX, encryptor.getVersion());
    }
    
    private String decryptFrom(String key) {
        // Decryption is always done with the version that was used for encryption.
        Integer version = getVersionAccountingForExceptions(key);
        Encryptor encryptor = encryptors.get(version);
        if (encryptor == null) {
            throw new BridgeServiceException("No encryptor can be found for version " + version);
        }
        String encryptedString = (String)acct.getCustomData().get(key);
        return (encryptedString != null) ? encryptor.decrypt(encryptedString) : null;
    }
    
    /**
     * Historically there have been two special cases: health Ids were stored with a format
     * for the version that wasn't generically applicable to other attributes in the customData object,
     * and phone numbers were stored with no separate version at all.
     * @param key
     * @return
     */
    private Integer getVersionAccountingForExceptions(String key) {
        Integer version = (Integer)acct.getCustomData().get(key+VERSION_SUFFIX);
        if (version == null) {
            // Special case #1: the original health id version format is being used (studyIdversion), not the newer per-field key format
            // (studyId_code_version)
            if (healthIdKey.equals(key)) {
                version = (Integer)acct.getCustomData().get(oldHealthIdVersionKey);
            } 
            // Special case #2: phone without a version string
            else if (PHONE_ATTRIBUTE.equals(key)) {
                version = 2;
            }
        }
        if (version == null) {
            throw new BridgeServiceException("No version for encryptor found for field " + key);
        }
        return version;
    }
    
    @Override
    public String toString() {
        return String.format("StormpathAccount [username=%s, firstName=%s, lastName=%s, email=%s, roles=%s]",
                getUsername(), getFirstName(), getLastName(), getEmail(), getRoles());
    }

}
