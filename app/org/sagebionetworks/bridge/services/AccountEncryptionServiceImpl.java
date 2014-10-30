package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.Study;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;

public class AccountEncryptionServiceImpl implements AccountEncryptionService {

    private BridgeEncryptor healthCodeEncryptorOld;
    private AesGcmEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;

    public void setHealthCodeEncryptorOld(BridgeEncryptor encryptor) {
        this.healthCodeEncryptorOld = encryptor;
    }

    public void setHealthCodeEncryptor(AesGcmEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }

    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    @Override
    public HealthId createAndSaveHealthCode(Study study, Account account) {
        final CustomData customData = account.getCustomData();
        final HealthId healthId = healthCodeService.create();
        final String encryptedHealthId = healthCodeEncryptor.encrypt(healthId.getId());
        final String healthIdKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
        customData.put(healthIdKey, encryptedHealthId);
        customData.put(BridgeConstants.CUSTOM_DATA_VERSION, 2);
        customData.save();
        return healthId;
    }

    @Override
    public HealthId getHealthCode(Study study, Account account) {
        final CustomData customData = account.getCustomData();
        final String healthIdKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
        final Object healthIdObj = customData.get(healthIdKey);
        if (healthIdObj != null) {
            int version = 1;
            Object versionObj = customData.get(BridgeConstants.CUSTOM_DATA_VERSION);
            if (versionObj != null) {
                version = (Integer)versionObj;
            }
            final String healthId = version == 2 ?
                    healthCodeEncryptor.decrypt((String) healthIdObj) :
                    healthCodeEncryptorOld.decrypt((String) healthIdObj);
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
        return null;
    }
}
