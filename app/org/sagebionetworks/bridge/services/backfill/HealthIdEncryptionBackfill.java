package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountEncryptionService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

/**
 * Backfills health ID encryption (for example, with a new key).
 */
public class HealthIdEncryptionBackfill extends AsyncBackfillTemplate {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    int getLockExpireInSeconds() {
        return 30 * 60;
    }

    @Override
    void doBackfill(final BackfillTask task, BackfillCallback callback) {
        List<Study> studies = studyService.getStudies();
        for (Study study : studies) {
            backfillForStudy(study, task.getId(), callback);
        }
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
                            ObjectNode node = MAPPER.createObjectNode();
                            node.put("studyIdentifier", study.getIdentifier());
                            node.put("account", account.getEmail());
                            node.put("operation", "Backfilled");
                            try {
                                return MAPPER.writeValueAsString(node);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
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
                                ObjectNode node = MAPPER.createObjectNode();
                                node.put("studyIdentifier", study.getIdentifier());
                                node.put("account", account.getEmail());
                                node.put("operation", "Recreated");
                                try {
                                    return MAPPER.writeValueAsString(node);
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }
                }
            }
        }
    }
}
