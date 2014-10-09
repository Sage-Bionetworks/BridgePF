package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

public class BackfillService {

    private final Logger logger = LoggerFactory.getLogger(BackfillService.class);

    private Client stormpathClient;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }
    public void setHealthCodeEncryptor(BridgeEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }
    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }

    /**
     * Clears Stormpath for user consent.
     */
    public int stormpathUserConsent(Study study) {
        int count = 0;
        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountList accounts = application.getAccounts();
        for (Account account : accounts) {
            final CustomData customData = account.getCustomData();
            final String studyKey = study.getKey();
            final String consentedKey = studyKey + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
            final Object consentedObj = customData.get(consentedKey);
            logger.info("[Stormpath directory: " + account.getDirectory().getName()
                    + ", Stormpath user: " + account.getEmail()
                    +", Stormpath consented: " + consentedObj + "]");
            if (consentedObj != null) {
                customData.remove(consentedKey);
                customData.save();
                count++;
            }
        }
        return count;
    }
}
