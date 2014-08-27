package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

import controllers.StudyControllerService;

public class BackfillService {

    private final Logger logger = LoggerFactory.getLogger(BackfillService.class);

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
     * Backfills user consent from Stormpath.
     */
    private int stormpathUserConsent(Study study) {
        int count = 0;
        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountList accounts = application.getAccounts();
        for (Account account : accounts) {
            final CustomData customData = account.getCustomData();
            final String studyKey = study.getKey();
            final String consentedKey = studyKey + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
            final Object consentedObj = customData.get(consentedKey);
            final String healthIdKey = studyKey + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            final Object healthIdObj = customData.get(healthIdKey);
            logger.info("Stormpath user: " + account.getEmail());
            logger.info("Stormpath consented: " + consentedObj);
            if (consentedObj != null && healthIdObj != null) {
                if ("true".equals(((String)consentedObj).toLowerCase())) {
                    String healthId = healthCodeEncryptor.decrypt((String) healthIdObj);
                    String healthCode = healthCodeService.getHealthCode(healthId);
                    StudyConsent studyConsent = studyConsentDao.getConsent(studyKey);
                    if (studyConsent == null) {
                        logger.warn("Missing study consent.");
                        studyConsent = new StudyConsent() {
                            @Override
                            public String getStudyKey() {
                                return studyKey;
                            }
                            @Override
                            public long getCreatedOn() {
                                return 1406325157000L; // July 25, 2014
                            }
                            @Override
                            public boolean getActive() {
                                return true;
                            }
                            @Override
                            public String getPath() {
                                return "conf/email-templates/neurod-consent.html";
                            }
                            @Override
                            public int getMinAge() {
                                return 17;
                            }
                        };
                    }
                    final boolean dynamoConsented = userConsentDao.hasConsented(healthCode, studyConsent);
                    logger.info("Dynamo consented: " + dynamoConsented);
                    if (!dynamoConsented) {
                        // Consent signature is not stored in Stormpath
                        String userName = account.getUsername();
                        ConsentSignature researchConsent = new ConsentSignature(userName, "1970-01-01");
                        userConsentDao.giveConsent(healthCode, studyConsent, researchConsent);
                        logger.info("Dynamo backfilled.");
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Backfills user consent from old schema to new schema.
     */
    public int dynamoUserConsent() {
        return userConsentDao.backfill();
    }
}
