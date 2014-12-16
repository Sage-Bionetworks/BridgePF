package org.sagebionetworks.bridge.services.backfill;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillStatus;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountEncryptionService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

/**
 * Backfills health ID encryption (for example, with a new key).
 */
public class HealthIdEncryptionBackfill extends AsyncBackfillTemplate {

    private Client stormpathClient;
    private StudyService studyService;
    private AesGcmEncryptor healthCodeEncryptor;
    private AccountEncryptionService accountEncryptionService;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    public void setHealthCodeEncryptor(AesGcmEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }
    public void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    @Override
    void doBackfill(final String user, final String name, BackfillCallback callback) {
        final String taskId = UUID.randomUUID().toString();
        callback.start(new BackfillTask() {
            @Override
            public String getId() {
                return taskId;
            }
            @Override
            public long getTimestamp() {
                return DateTime.now(DateTimeZone.UTC).getMillis();
            }
            @Override
            public String getName() {
                return name;
            }
            @Override
            public String getDescription() {
                return "Backfills health ID encryption.";
            }
            @Override
            public String getUser() {
                return user;
            }
            @Override
            public String getStatus() {
                return BackfillStatus.SUBMITTED.name();
            }
        });
        List<Study> studies = studyService.getStudies();
        for (Study study : studies) {
            backfillForStudy(study, taskId, callback);
        }
        callback.done();
    }

    @Override
    int getExpireInSeconds() {
        return 30 * 60;
    }

    private void backfillForStudy(final Study study, final String taskId, final BackfillCallback callback) {
        Application application = StormpathFactory.getStormpathApplication(stormpathClient);
        StormpathAccountIterator iterator = new StormpathAccountIterator(application);
        while (iterator.hasNext()) {
            List<Account> accountList = iterator.next();
            for (final Account account : accountList) {
                final CustomData customData = account.getCustomData();
                final String studyKey = study.getIdentifier();
                final String healthIdKey = studyKey + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
                final Object healthIdObj = customData.get(healthIdKey);
                if (healthIdObj == null) {
                    // Backfill accounts that have no health id
                    accountEncryptionService.createAndSaveHealthCode(study, account);
                    callback.newRecords(new BackfillRecord() {
                        @Override
                        public String getTaskId() {
                            return taskId;
                        }
                        @Override
                        public long getTimestamp() {
                            return DateTime.now(DateTimeZone.UTC).getMillis();
                        }
                        @Override
                        public String getRecord() {
                            return "{\"study\": \"" + study.getIdentifier()
                                    + "\", \"account\": \"" + account.getEmail()
                                    + "\", \"operation\": \"created\"}";
                        }
                    });
                } else {
                    // Backfill accounts that are missing health codes -- this should be only test artifacts and
                    // should not exist in staging and prod
                    HealthId healthId = accountEncryptionService.getHealthCode(study, account);
                    String healthCode = healthId.getCode();
                    if (healthCode == null) {
                        accountEncryptionService.createAndSaveHealthCode(study, account);
                        callback.newRecords(new BackfillRecord() {
                            @Override
                            public String getTaskId() {
                                return taskId;
                            }
                            @Override
                            public long getTimestamp() {
                                return DateTime.now(DateTimeZone.UTC).getMillis();
                            }
                            @Override
                            public String getRecord() {
                                return "{\"study\": \"" + study.getIdentifier()
                                        + "\", \"account\": \"" + account.getEmail()
                                        + "\", \"operation\": \"recreated\"}";
                            }
                        });
                    }
                }
            }
        }
    }
}
