package org.sagebionetworks.bridge.services.backfill;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Iterator;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Backfills study IDs to the health code table.
 */
public class StudyIdBackfill extends AsyncBackfillTemplate  {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyIdBackfill.class);

    private BackfillRecordFactory backfillRecordFactory;
    private StudyService studyService;
    private AccountDao accountDao;
    private HealthCodeDao healthCodeDao;
    private HealthCodeService healthCodeService;

    @Autowired
    public void setBackfillRecordFactory(BackfillRecordFactory backfillRecordFactory) {
        this.backfillRecordFactory = backfillRecordFactory;
    }

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
    
    @Autowired
    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    

    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }

    @Override
    void doBackfill(final BackfillTask task, final BackfillCallback callback) {
        
        for (Iterator<Account> i = accountDao.getAllAccounts(); i.hasNext();) {
            Account account = i.next();
            Study study = studyService.getStudy(account.getStudyIdentifier());
            
            HealthId mapping = healthCodeService.getMapping(account);
            if (mapping != null) {
                try {
                    String healthCode = mapping.getCode();
                    if (healthCode != null) {
                        final String studyId = healthCodeDao.getStudyIdentifier(healthCode);
                        if (isBlank(studyId)) {
                            String msg = "Backfill needed as study ID is blank.";
                            callback.newRecords(backfillRecordFactory.createOnly(task, study, account, msg));
                        } else {
                            String msg = "Study ID already exists.";
                            callback.newRecords(backfillRecordFactory.createOnly(task, study, account, msg));
                        }
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                    String msg = e.getClass().getName() + " " + e.getMessage();
                    callback.newRecords(backfillRecordFactory.createOnly(task, study, account, msg));
                }
            }
        }
    }
}
