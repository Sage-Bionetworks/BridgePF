package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountEncryptionService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;

/**
 * Backfills health ID and health code.
 */
public class HealthCodeBackfill extends AsyncBackfillTemplate {

    private Client stormpathClient;
    private StudyService studyService;
    private AccountEncryptionService accountEncryptionService;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    public void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    private BackfillRecordFactory backfillRecordFactory;
    public void setBackfillRecordFactory(BackfillRecordFactory backfillRecordFactory) {
        this.backfillRecordFactory = backfillRecordFactory;
    }

    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }

    @Override
    void doBackfill(final BackfillTask task, BackfillCallback callback) {
        final List<Study> studies = studyService.getStudies();
        Application application = StormpathFactory.getStormpathApplication(stormpathClient);
        StormpathAccountIterator iterator = new StormpathAccountIterator(application);
        while (iterator.hasNext()) {
            List<Account> accountList = iterator.next();
            for (final Account account : accountList) {
                for (Study study : studies) {
                    HealthId healthId = accountEncryptionService.getHealthCode(study, account);
                    if (healthId == null) {
                        // Backfill the account that have no health code mapping in the study.
                        // This happens when the user creates a new account and consents in a study
                        // and has not consented in other studies yet.
                        healthId = accountEncryptionService.createAndSaveHealthCode(study, account);
                        callback.newRecords(backfillRecordFactory.createAndSave(
                                task, study, account, "health code created"));
                    } 
                }
            }
        }
    }
}
