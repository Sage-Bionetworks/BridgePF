package org.sagebionetworks.bridge.backfill;

import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.crypto.EncryptorUtil;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

import controllers.StudyControllerService;

public class HealthCodeBackfill {

    private BridgeConfig config;
    private Client stormpathClient;
    private StudyControllerService studyControllerService;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;

    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public void setHealthCodeEncryptor(BridgeEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }

    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    /**
     * For health-ID to health-code mapping, regenerates the health IDs
     * encrypts them, and publishes the new health IDs to Stormpath.
     */
    public int resetHealthId() {
        int total = 0;
        for (Study study : studyControllerService.getStudies()) {
            total = total + resetHealthId(study);
        }
        return total;
    }

    /**
     * For health-ID to health-code mapping, regenerates the health IDs
     * encrypts them, and publishes the new health IDs to Stormpath.
     */
    public int resetHealthId(Study study) {
        int count = 0;
        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountList accounts = application.getAccounts();
        for (Account account : accounts) {
            CustomData customData = account.getCustomData();
            final String key = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            final String hcEncrypted = (String)customData.get(key);
            final Object version = customData.get(BridgeConstants.CUSTOM_DATA_VERSION);
            if (version == null && hcEncrypted != null) {
                PBEStringEncryptor encryptor = EncryptorUtil.getEncryptorOld(
                        config.getHealthCodePassword(), config.getHealthCodeSalt());
                final String healthCode = encryptor.decrypt(hcEncrypted);
                final String healthId = healthCodeService.resetHealthId(healthCode);
                final String hiEncrypted = healthCodeEncryptor.encrypt(healthId);
                customData.put(key, hiEncrypted);
                customData.put(BridgeConstants.CUSTOM_DATA_VERSION, 1);
                customData.save();
                count++;
            }
        }
        return count;
    }
}
