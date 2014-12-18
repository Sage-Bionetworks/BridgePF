package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
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

/**
 * Backfills study IDs to the health code table.
 */
public class StudyIdBackfill extends AsyncBackfillTemplate  {

    private static final ObjectMapper MAPPER = BridgeObjectMapper.get();

    private StudyService studyService;
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    private Client stormpathClient;
    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    private AccountEncryptionService accountEncryptionService;
    public void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    private HealthCodeDao healthCodeDao;
    public void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }

    @Override
    void doBackfill(final BackfillTask task, final BackfillCallback callback) {
        final List<Study> studies = studyService.getStudies();
        Application application = StormpathFactory.getStormpathApplication(stormpathClient);
        StormpathAccountIterator iterator = new StormpathAccountIterator(application);
        while (iterator.hasNext()) {
            List<Account> accountList = iterator.next();
            for (final Account account : accountList) {
                for (final Study study : studies) {
                    HealthId healthId = accountEncryptionService.getHealthCode(study, account);
                    try {
                        healthCodeDao.setStudyId(healthId.getCode(), study.getIdentifier());
                        callback.newRecords(new BackfillRecord() {
                            @Override
                            public String getTaskId() {
                                return task.getId();
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
                                node.put("operation", "Study ID backfilled");
                                try {
                                    return MAPPER.writeValueAsString(node);
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    } catch (final RuntimeException e) {
                        callback.newRecords(new BackfillRecord() {
                            @Override
                            public String getTaskId() {
                                return task.getId();
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
                                node.put("operation", e.getMessage());
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
