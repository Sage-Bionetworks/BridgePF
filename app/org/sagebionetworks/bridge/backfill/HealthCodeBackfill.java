package org.sagebionetworks.bridge.backfill;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
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

    private Client stormpathClient;
    private StudyControllerService studyControllerService;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;

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
    public void regerateHealthId() {
        for (Study study : studyControllerService.getStudies()) {
            regerateHealthId(study);
        }
    }

    /**
     * For health-ID to health-code mapping, regenerates the health IDs
     * encrypts them, and publishes the new health IDs to Stormpath.
     */
    public void regerateHealthId(Study study) {
        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountList accounts = application.getAccounts();
        for (Account account : accounts) {
            CustomData customData = account.getCustomData();
            String key = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            // TODO:
            // HealthId healthId = healthCodeService.resetHealthId();
            // String encryptedId = healthCodeEncryptor.encrypt(healthId.getId());
            // data.put(key, encryptedId);
            // data.put(BridgeConstants.CUSTOM_DATA_VERSION, 1);
            // customData.save();
        }
    }
}
