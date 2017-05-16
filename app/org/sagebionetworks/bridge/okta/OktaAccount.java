package org.sagebionetworks.bridge.okta;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.OKTA_NAME_PLACEHOLDER_STRING;
import static org.sagebionetworks.bridge.BridgeConstants.STORMPATH_NAME_PLACEHOLDER_STRING;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.okta.sdk.models.users.User;
import com.okta.sdk.models.users.UserProfile;

@BridgeTypeName("Account")
public class OktaAccount implements Account {
    
    public static final String ROLES = "bridge_roles";
    public static final String STATUS = "bridge_status";
    
    private static final TypeReference<List<ConsentSignature>> CONSENT_SIGNATURES_TYPE = new TypeReference<List<ConsentSignature>>() {};
    private static final ObjectMapper MAPPER = BridgeObjectMapper.get();
    private final Map<SubpopulationGuid, List<ConsentSignature>> allSignatures;
    
    private static final String PHONE_ATTRIBUTE = "phone";
    private static final String HEALTH_CODE_SUFFIX = "_code";
    private static final String CONSENT_SIGNATURE_SUFFIX = "_consent_signature";
    private static final String CONSENT_SIGNATURES_SUFFIX = "_consent_signatures";
    private static final String VERSION_SUFFIX = "_version";
    private static final String OLD_VERSION_SUFFIX = "version";
    
    private final StudyIdentifier studyIdentifier;
    private final SortedMap<Integer, BridgeEncryptor> encryptors;
    private final String healthIdKey;
    private final String oldHealthIdVersionKey;
    private final String oldConsentSignatureKey;
    
    private UserProfile userProfile;
    private User user;
    private ImmutableSet<Roles> roles;
    private String healthCode;
    
    OktaAccount(StudyIdentifier studyIdentifier, List<? extends SubpopulationGuid> subpopGuids, User user,
            UserProfile userProfile, SortedMap<Integer, BridgeEncryptor> encryptors) {
        checkNotNull(studyIdentifier);
        checkNotNull(subpopGuids);
        checkNotNull(userProfile);
        checkNotNull(encryptors);

        String studyId = studyIdentifier.getIdentifier();
        
        setUserProfile(userProfile);
        setUser(user);
        this.studyIdentifier = studyIdentifier;
        this.encryptors = encryptors;
        this.healthIdKey = studyId + HEALTH_CODE_SUFFIX;
        this.oldHealthIdVersionKey = studyId + OLD_VERSION_SUFFIX;
        this.oldConsentSignatureKey = studyId + CONSENT_SIGNATURE_SUFFIX;
        this.allSignatures = Maps.newHashMap();
        
        for (SubpopulationGuid subpopGuid : subpopGuids) {
            List<ConsentSignature> signatures = decryptJSONFrom(subpopGuid.getGuid()+CONSENT_SIGNATURES_SUFFIX, CONSENT_SIGNATURES_TYPE);
            if (signatures == null || signatures.isEmpty()) {
                ConsentSignature sig = decryptJSONFrom(subpopGuid.getGuid()+CONSENT_SIGNATURE_SUFFIX, ConsentSignature.class);
                if (sig != null) {
                    getConsentSignatureHistory(subpopGuid).add(sig);
                }
            } else {
                getConsentSignatureHistory(subpopGuid).addAll(signatures);
            }
        }
    }

    public UserProfile getUserProfile() {
        for (Map.Entry<SubpopulationGuid, List<ConsentSignature>> entry : allSignatures.entrySet()) {
            encryptJSONTo(entry.getKey().getGuid()+CONSENT_SIGNATURES_SUFFIX, entry.getValue());
        }
        userProfile.getUnmapped().put(ROLES, Joiner.on(", ").join(roles));
        return userProfile;
    }
    public void setUserProfile(UserProfile profile) {
        this.userProfile = profile;
        this.roles = ImmutableSet.copyOf(convertRolesQuietly((String)userProfile.getUnmapped().get(ROLES)));
    }
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }
    
    private static Set<Roles> convertRolesQuietly(String groups) {
        Set<Roles> roleSet = new HashSet<>();
        if (StringUtils.isNotBlank(groups)) {
            List<String> groupList = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(groups);
            for (String group : groupList) {
                roleSet.add(Roles.valueOf(group.toUpperCase()));
            }
        }
        return roleSet;
    }
    @Override
    public String getFirstName() {
        String firstName = userProfile.getFirstName();
        return (OKTA_NAME_PLACEHOLDER_STRING.equals(firstName)) ? null : firstName;
    }
    @Override
    public void setFirstName(String firstName) {
        if (isBlank(firstName)|| STORMPATH_NAME_PLACEHOLDER_STRING.equals(firstName)) {
            userProfile.setFirstName(OKTA_NAME_PLACEHOLDER_STRING);
        } else {
            userProfile.setFirstName(firstName);    
        }
    }
    @Override
    public String getLastName() {
        String lastName = userProfile.getLastName();
        return (OKTA_NAME_PLACEHOLDER_STRING.equals(lastName)) ? null : lastName;
    }
    @Override
    public void setLastName(String lastName) {
        if (isBlank(lastName) || STORMPATH_NAME_PLACEHOLDER_STRING.equals(lastName)) {
            userProfile.setLastName(OKTA_NAME_PLACEHOLDER_STRING);
        } else {
            userProfile.setLastName(lastName);    
        }
    }
    @Override
    public String getAttribute(String name) {
        return decryptFrom(name);
    }
    @Override
    public void setAttribute(String name, String value) {
        encryptTo(name, value);
    }
    @Override
    public String getEmail() {
        return userProfile.getEmail();
    }
    @Override
    public void setEmail(String email) {
        userProfile.setEmail(email);
        userProfile.setLogin(email);
    }
    @Override
    public List<ConsentSignature> getConsentSignatureHistory(SubpopulationGuid subpopGuid) {
        allSignatures.putIfAbsent(subpopGuid, Lists.newArrayList());
        return allSignatures.get(subpopGuid);
    }
    @Override
    public Map<SubpopulationGuid, List<ConsentSignature>> getAllConsentSignatureHistories() {
        return allSignatures;
    }
    public String getHealthId(){
        return decryptFrom(healthIdKey);
    }
    @Override
    public String getHealthCode(){
        return healthCode;
    }
    @Override
    public void setHealthId(HealthId healthId) {
        if (healthId != null) {
            encryptTo(healthIdKey, healthId.getId());
            this.healthCode = healthId.getCode();
        }
    }
    @Override
    public AccountStatus getStatus() {
        String statusString = (String)userProfile.getUnmapped().get("bridge_status");
        return (statusString == null) ? null : AccountStatus.valueOf(statusString);
    }
    @Override
    public void setStatus(AccountStatus status) {
        userProfile.getUnmapped().put("bridge_status", status.name());
    }
    @Override
    public StudyIdentifier getStudyIdentifier() {
        return studyIdentifier;
    }
    @Override
    public Set<Roles> getRoles() {
        return roles;
    }
    @Override
    public void setRoles(Set<Roles> roles) {
        this.roles = (roles == null) ? ImmutableSet.of() : ImmutableSet.copyOf(roles);
    }
    @Override
    public String getId() {
        return user.getId();
    }
    @Override
    public DateTime getCreatedOn() {
        return user.getCreated();
    }
    
    private void encryptJSONTo(String key, Object value) {
        if (value == null) {
            userProfile.getUnmapped().remove(key);
            userProfile.getUnmapped().remove(key+VERSION_SUFFIX);
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
    
    private <T> T decryptJSONFrom(String key, TypeReference<T> reference) {
        try {
            String jsonString = decryptFrom(key);
            if (jsonString == null) {
                return null;
            }
            return MAPPER.readValue(jsonString, reference);
        } catch(IOException e) {
            String message = String.format("Could not retrieve %s due to malformed JSON: %s", key, e.getMessage());
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
            userProfile.getUnmapped().remove(key);
            userProfile.getUnmapped().remove(key+VERSION_SUFFIX);
            return;
        }
        // Encryption is always done with the most recent encryptor, which is last in the list (most revent version #)
        Integer encryptorKey = encryptors.lastKey();
        BridgeEncryptor encryptor = encryptors.get(encryptorKey);

        String encrypted = encryptor.encrypt(value);
        userProfile.getUnmapped().put(key+VERSION_SUFFIX, encryptor.getVersion());
        userProfile.getUnmapped().put(key, encrypted);
    }
    
    private String decryptFrom(String key) {
        String encryptedString = (String)userProfile.getUnmapped().get(key);
        if (encryptedString == null) {
            return null;
        }
        // Decryption is always done with the version that was used for encryption.
        Integer version = getVersionAccountingForExceptions(key);
        BridgeEncryptor encryptor = encryptors.get(version);
        if (encryptor == null) {
            throw new BridgeServiceException("No encryptor can be found for version " + version);
        }
        return encryptor.decrypt(encryptedString);
    }
    /**
     * Historically there have been two special cases: health Ids were stored with a format
     * for the version that wasn't generically applicable to other attributes in the customData object,
     * and phone numbers were stored with no separate version at all.
     * @param key
     * @return
     */
    private Integer getVersionAccountingForExceptions(String key) {
        String versionKey = key+VERSION_SUFFIX;
        Integer version = (Integer)userProfile.getUnmapped().get(versionKey);
        if (version == null) {
            // Special case #1: the original health id version format is being used (studyIdversion), not the newer per-field key format
            // (studyId_code_version)
            if (healthIdKey.equals(key)) {
                versionKey = oldHealthIdVersionKey;
                version = (Integer)userProfile.getUnmapped().get(versionKey);
            } 
            // Special case #2: phone without a version string
            else if (PHONE_ATTRIBUTE.equals(key)) {
                version = 2;
            }
            // Special case #3: existing consent signature has no version. Again, assume version 2 for now. 
            else if (oldConsentSignatureKey.equals(key)) {
                version = 2;
            }
        }
        if (version == null) {
            // Get the most recent key. We've only ever used v2 in production so in the rare case where we 
            // don't have the version of the encryptor saved alongside the attribute, this should be correct.
            version = encryptors.lastKey();
        }
        return version;
    }
}
