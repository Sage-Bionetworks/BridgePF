package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.models.Backfill;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

/**
 * Scopes the version of custom data with study ID.
 */
public class HealthIdVersionBackfill implements BackfillService {

    private final Logger logger = LoggerFactory.getLogger(HealthIdVersionBackfill.class);

    private Client stormpathClient;
    private StudyService studyService;
    private BridgeEncryptor healthCodeEncryptorOld;
    private AesGcmEncryptor healthCodeEncryptor;
    private AesGcmEncryptor healthCodeEncryptorLocal;

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
    public void setHealthCodeEncryptorLocal(AesGcmEncryptor encryptor) {
        this.healthCodeEncryptorLocal = encryptor;
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
                final String versionKeyOld = BridgeConstants.CUSTOM_DATA_VERSION;
                customData.remove(versionKeyOld);
                customData.save();

                final String healthIdKey = studyKey + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
                final Object healthIdObj = customData.get(healthIdKey);
                final String versionKeyNew = studyKey + BridgeConstants.CUSTOM_DATA_VERSION;
                if (healthIdObj == null) {
                    customData.remove(versionKeyNew);
                    customData.save();
                } else {
                    boolean updated = false;
                    try {
                        healthCodeEncryptorOld.decrypt((String)healthIdObj);
                        customData.put(versionKeyNew, Integer.valueOf(1));
                        customData.save();
                        updated = true;
                    } catch (Exception e) {
                        updated = false;
                    }
                    if (!updated) {
                        try {
                            healthCodeEncryptor.decrypt((String)healthIdObj);
                            customData.put(versionKeyNew, Integer.valueOf(2));
                            customData.save();
                            updated = true;
                        } catch (Exception e) {
                            updated = false;
                        }
                    }
                    if (!updated) {
                        try {
                            String decrypted = healthCodeEncryptorLocal.decrypt((String)healthIdObj);
                            String encrypted = healthCodeEncryptor.encrypt(decrypted);
                            customData.put(healthIdKey, encrypted);
                            customData.put(versionKeyNew, Integer.valueOf(2));
                            customData.save();
                            updated = true;
                        } catch (Exception e) {
                            updated = false;
                        }
                    }
                    if (!updated) {
                        logger.error("Failed to update account " + account.getEmail() + " in study " + study.getName());
                    }
                }
            }
        }
        return count;
    }
}
