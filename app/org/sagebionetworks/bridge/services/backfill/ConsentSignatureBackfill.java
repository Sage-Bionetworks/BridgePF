package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountEncryptionService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;

/**
 * Backfills consent signatures to Stormpath.
 */
public class ConsentSignatureBackfill extends AsyncBackfillTemplate {

    private BackfillRecordFactory backfillRecordFactory;

    private StudyService studyService;
    private Client stormpathClient;
    private AccountEncryptionService accountEncryptionService;
    private UserConsentDao userConsentDao;

    @Autowired
    public void setBackfillRecordFactory(BackfillRecordFactory backfillRecordFactory) {
        this.backfillRecordFactory = backfillRecordFactory;
    }

    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    @Autowired
    public void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    @Autowired
    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }

    @Override
    int getLockExpireInSeconds() {
        return 60 * 60;
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        final List<Study> studies = studyService.getStudies();
        Application application = StormpathFactory.getStormpathApplication(stormpathClient);
        StormpathAccountIterator iterator = new StormpathAccountIterator(application);
        while (iterator.hasNext()) {
            List<Account> accountList = iterator.next();
            for (final Account account : accountList) {
                for (final Study study : studies) {
                    backfillAccount(task, callback, study, account);
                }
            }
        }
    }

    private void backfillAccount(BackfillTask task, BackfillCallback callback, Study study, Account account) {
            HealthId healthId = null;
            try {
                healthId = accountEncryptionService.getHealthCode(study, account);
            } catch (Exception ex) {
                callback.newRecords(backfillRecordFactory.createOnly(task, study, account, ex.getMessage()));
            }
            if (healthId == null) {
                callback.newRecords(backfillRecordFactory.createOnly(
                        task, study, account, "Missing health code. Backfill skipped."));
            } else {
                try {
                    userConsentDao.removeConsentSignature(healthId.getCode(), study.getIdentifier());
                    callback.newRecords(backfillRecordFactory.createAndSave(
                            task, study, account, "Consent signature removed from DynamoDB."));
                } catch (EntityNotFoundException ex) {
                    callback.newRecords(backfillRecordFactory.createOnly(
                            task, study, account, "Missing consent signature in DynamoDB. Backfill skipped."));
                }
            }
    }
}
