package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountEncryptionService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;

/**
 * Backfills consent signatures to Stormpath.
 */
public class ConsentSignatureBackfill extends AsyncBackfillTemplate  {

    private BackfillRecordFactory backfillRecordFactory;
    public void setBackfillRecordFactory(BackfillRecordFactory backfillRecordFactory) {
        this.backfillRecordFactory = backfillRecordFactory;
    }

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

    private UserConsentDao userConsentDao;
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
                    ConsentSignature consentSignature = accountEncryptionService.getConsentSignature(study, account);
                    if (consentSignature != null) {
                        backfillRecordFactory.createOnly(task, study, account, "Already in Stormpath.");
                    } else {
                        HealthId healthId = accountEncryptionService.getHealthCode(study, account);
                        if (healthId == null) {
                            backfillRecordFactory.createOnly(task, study, account, "Missing health code. Backfill skipped.");
                        }
                        consentSignature = userConsentDao.getConsentSignature(healthId.getCode(), study.getIdentifier());
                        if (consentSignature == null) {
                            backfillRecordFactory.createOnly(task, study, account, "Missing consent signature in DynamoDB. Backfill skipped.");
                        } else {
                            accountEncryptionService.putConsentSignature(study, account, consentSignature);
                            backfillRecordFactory.createAndSave(task, study, account, "Backfilled from DynamoDB to Stormpath.");
                        }
                    }
                }
            }
        }
    }
}
