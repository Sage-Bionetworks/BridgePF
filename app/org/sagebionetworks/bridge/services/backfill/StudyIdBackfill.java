package org.sagebionetworks.bridge.services.backfill;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Iterator;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Backfills study IDs to the health code table.
 */
@Component
public class StudyIdBackfill extends AsyncBackfillTemplate  {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyIdBackfill.class);

    private StudyService studyService;
    private AccountDao accountDao;
    private HealthCodeDao healthCodeDao;

    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao; 
    }

    @Autowired
    public void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }
    
    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }

    @Override
    void doBackfill(final BackfillTask task, final BackfillCallback callback) {
        
        for (Iterator<Account> i = accountDao.getAllAccounts(); i.hasNext();) {
            // This ensures the healthCode is created.
            Account acct = i.next();
            Study study = studyService.getStudy(acct.getStudyIdentifier());
            Account account = accountDao.getAccount(study, acct.getId());
            try {
                String healthCode = account.getHealthCode();
                final String studyId = healthCodeDao.getStudyIdentifier(healthCode);
                if (isBlank(studyId)) {
                    String msg = "Backfill needed as study ID is blank.";
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, study, account, msg));
                } else {
                    String msg = "Study ID already exists.";
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, study, account, msg));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
                String msg = e.getClass().getName() + " " + e.getMessage();
                callback.newRecords(getBackfillRecordFactory().createOnly(task, study, account, msg));
            }
        }
    }
}
