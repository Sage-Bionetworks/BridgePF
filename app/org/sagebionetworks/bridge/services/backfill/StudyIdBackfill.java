package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.models.BackfillTask;
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

/**
 * Backfills study IDs to the health code table.
 */
public class StudyIdBackfill extends AsyncBackfillTemplate  {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyIdBackfill.class);


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
                    if (healthId == null) {
                        healthId = accountEncryptionService.createAndSaveHealthCode(study, account);
                    }
                    try {
                        String healthCode = healthId.getCode();
                        if (healthCode != null) {
                            healthCodeDao.setStudyId(healthCode, study.getIdentifier());
                            callback.newRecords(createRecord(task, study, account, "backfilled"));
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                        String operation = e.getClass().getName() + " " + e.getMessage();
                        callback.newRecords(createRecord(task, study, account, operation));
                    }
                }
            }
        }
    }
}
