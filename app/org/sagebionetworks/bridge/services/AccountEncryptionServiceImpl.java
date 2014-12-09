package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;

public class AccountEncryptionServiceImpl implements AccountEncryptionService {

    private static final int CURRENT_VERSION = 2;
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
    public void saveConsentSignature(ConsentSignature consentSignature, Account account) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.valueToTree(consentSignature);
        healthCodeEncryptor.encrypt(json.asText());
    }

    private String getHealthIdKey(Study study) {
        return study.getIdentifier() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
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
}
