package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.Study;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;

public class AccountEncryptionServiceImpl implements AccountEncryptionService {

    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;

    public void setHealthCodeEncryptor(BridgeEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }

    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    @Override
    public HealthId createAndSaveHealthCode(Study study, Account account) {
        CustomData customData = account.getCustomData();
        HealthId healthId = healthCodeService.create();
        String healthIdKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
        customData.put(healthIdKey, healthCodeEncryptor.encrypt(healthId.getId()));
        customData.put(BridgeConstants.CUSTOM_DATA_VERSION, 1);
        customData.save();
        return healthId;
    }

    @Override
    public HealthId getHealthCode(Study study, Account account) {
        final CustomData customData = account.getCustomData();
        final String healthIdKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
        Object healthIdObj = customData.get(healthIdKey);
        if (healthIdObj != null) {
            final String healthId = healthCodeEncryptor.decrypt((String) healthIdObj);
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
