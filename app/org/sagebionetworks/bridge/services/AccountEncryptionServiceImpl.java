package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;

public class AccountEncryptionServiceImpl implements AccountEncryptionService {

    private static final int CURRENT_VERSION = 2;
    private final ObjectMapper mapper = new ObjectMapper();
    private AesGcmEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;

    public void setHealthCodeEncryptor(AesGcmEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }

    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    @Override
    public HealthId createAndSaveHealthCode(Study study, Account account) {
        checkNotNull(study);
        checkNotNull(account);
        final CustomData customData = account.getCustomData();
        final HealthId healthId = healthCodeService.create();
        final String encryptedHealthId = healthCodeEncryptor.encrypt(healthId.getId());
        customData.put(getHealthIdKey(study), encryptedHealthId);
        customData.put(getVersionKey(study), CURRENT_VERSION);
        customData.save();
        return healthId;
    }

    @Override
    public HealthId getHealthCode(Study study, Account account) {
        checkNotNull(study);
        checkNotNull(account);
        CustomData customData = account.getCustomData();
        Object healthIdObj = customData.get(getHealthIdKey(study));
        if (healthIdObj == null) {
            return null;
        }
        Object versionObj = customData.get(getVersionKey(study));
        return (getHealthId(healthIdObj, versionObj));
    }

    @Override
    public void putConsentSignature(Study study, Account account, ConsentSignature consentSignature) {
        JsonNode json = mapper.valueToTree(consentSignature);
        try {
            String encrypted = healthCodeEncryptor.encrypt(mapper.writeValueAsString(json));
            CustomData customData = account.getCustomData();
            customData.put(getConsentSignatureKey(study), encrypted);
            customData.save();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ConsentSignature getConsentSignature(Study study, Account account) {
        CustomData customData = account.getCustomData();
        Object obj = customData.get(getConsentSignatureKey(study));
        if (obj == null) {
            return null;
        }
        return decryptConsentSignature((String)obj);
    }

    @Override
    public ConsentSignature removeConsentSignature(Study study, Account account) {
        CustomData customData = account.getCustomData();
        Object obj = customData.remove(getConsentSignatureKey(study));
        if (obj == null) {
            return null;
        }
        customData.save();
        return decryptConsentSignature((String)obj);
    }

    private String getHealthIdKey(Study study) {
        return study.getIdentifier() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
    }

    private String getConsentSignatureKey(Study study) {
        return study.getIdentifier() + BridgeConstants.CUSTOM_DATA_CONSENT_SIGNATURE_SUFFIX;
    }

    private String getVersionKey(Study study) {
        return study.getIdentifier() + BridgeConstants.CUSTOM_DATA_VERSION;
    }

    private HealthId getHealthId(final Object healthIdObj, final Object versionObj) {
        final String healthId = healthCodeEncryptor.decrypt((String) healthIdObj);;
        final String healthCode = healthCodeService.getHealthCode(healthId);
        return new HealthId() {
            @Override
            public String getId() {
                return healthId;
            }
            @Override
            public String getCode() {
                return healthCode;
            }
        };
    }

    private ConsentSignature decryptConsentSignature(String encrypted) {
        String jsonText = healthCodeEncryptor.decrypt(encrypted);
        try {
            JsonNode json = mapper.readTree(jsonText);
            return ConsentSignature.createFromJson(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
