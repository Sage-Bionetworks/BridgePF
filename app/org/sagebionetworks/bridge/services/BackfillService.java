package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

import controllers.StudyControllerService;

public class BackfillService {

    private Client stormpathClient;
    private StudyControllerService studyControllerService;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;

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
    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }

    public int stormpathUserConsent() {
        int count = 0;
        for (Study study : studyControllerService.getStudies()) {
            count = count + stormpathUserConsent(study);
        }
        return count;
    }

    /**
     * Backfill user consent from Stormpath.
     */
    public int stormpathUserConsent(Study study) {
        int count = 0;
        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountList accounts = application.getAccounts();
        for (Account account : accounts) {
            CustomData customData = account.getCustomData();
            String studyKey = study.getKey();
            String customDataKey = studyKey + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
            Object consentedObj = customData.get(customDataKey);
            String healthIdKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            Object healthIdObj = customData.get(healthIdKey);
            if (consentedObj != null && healthIdObj != null) {
                boolean consented = (Boolean)consentedObj;
                if (consented) {
                    String healthId = healthCodeEncryptor.decrypt((String) healthIdObj);
                    String healthCode = healthCodeService.getHealthCode(healthId);
                    StudyConsent studyConsent = studyConsentDao.getConsent(studyKey);
                    if (!userConsentDao.hasConsented(healthCode, studyConsent)) {
                        // Consent signature is not stored in Stormpath
                        String userName = account.getUsername();
                        ConsentSignature researchConsent = new ConsentSignature(userName, "1970/01/01");
                        userConsentDao.giveConsent(healthCode, studyConsent, researchConsent);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Backfill user consent from old schema to new schema.
     */
    public int userConsent() {
        return userConsentDao.backfill();
    }
}
