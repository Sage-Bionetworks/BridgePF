package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.models.Backfill;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountEncryptionService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

/**
 * Backfills health ID encryption (for example, with a new key).
 */
public class HealthIdEncryptionBackfill implements BackfillService {

    private final Logger logger = LoggerFactory.getLogger(HealthIdEncryptionBackfill.class);

    private Client stormpathClient;
    private StudyService studyService;
    private BridgeEncryptor healthCodeEncryptorOld;
    private AesGcmEncryptor healthCodeEncryptor;
    private AccountEncryptionService accountEncryptionService;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    public void setHealthCodeEncryptorOld(BridgeEncryptor encryptor) {
        this.healthCodeEncryptorOld = encryptor;
    }
    public void setHealthCodeEncryptor(AesGcmEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }
    public void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    @Override
    public Backfill backfill() {
        int count = 0;
        List<Study> studies = studyService.getStudies();
        for (Study study : studies) {
            count = count + backfillForStudy(study);
        }
        Backfill backfill = new Backfill("healthIdEncryptionBackfill");
        backfill.setCompleted(true);
        backfill.setCount(count);
        return backfill;
    }

    private int backfillForStudy(Study study) {
        int count = 0;
        Application application = StormpathFactory.getStormpathApplication(stormpathClient);
        StormpathAccountIterator iterator = new StormpathAccountIterator(application);
        while (iterator.hasNext()) {
            List<Account> accountList = iterator.next();
            for (Account account : accountList) {
                final CustomData customData = account.getCustomData();
                final String studyKey = study.getIdentifier();
                final String healthIdKey = studyKey + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
                final Object healthIdObj = customData.get(healthIdKey);
                if (healthIdObj == null) {
                    // Backfill accounts that have no health id
                    accountEncryptionService.createAndSaveHealthCode(study, account);
                    count++;
                    logger.info("Created health code for account " + account.getEmail() + " in study " + study.getName());
                } else {
                    final String versionKey = studyKey + BridgeConstants.CUSTOM_DATA_VERSION;
                    int version = 1;
                    Object versionObj = customData.get(versionKey);
                    if (versionObj != null) {
                        version = (Integer)versionObj;
                    }
                    if (version != 2) {
                        // Backfill accounts that still use old encryption
                        String healthId = healthCodeEncryptorOld.decrypt((String) healthIdObj);
                        String encryptedHealthId = healthCodeEncryptor.encrypt(healthId);
                        customData.put(healthIdKey, encryptedHealthId);
                        customData.put(versionKey, 2);
                        customData.save();
                        count++;
                        logger.info("Backfilled encryption for account " + account.getEmail() + " in study " + study.getName());
                    }
                    // Backfill accounts that are missing health codes -- this should be only test artifacts and
                    // should not exist in staging and prod
                    HealthId healthId = accountEncryptionService.getHealthCode(study, account);
                    String healthCode = healthId.getCode();
                    if (healthCode == null) {
                        accountEncryptionService.createAndSaveHealthCode(study, account);
                        logger.info("Re-created health code for account " + account.getEmail() + " in study " + study.getName());
                    }
                }
            }
        }
        return count;
    }
}
