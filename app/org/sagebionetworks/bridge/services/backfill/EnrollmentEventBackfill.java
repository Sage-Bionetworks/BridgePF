package org.sagebionetworks.bridge.services.backfill;

import java.util.Iterator;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.TaskEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("enrollmentEventBackfill")
public class EnrollmentEventBackfill extends AsyncBackfillTemplate {
    
    private BackfillRecordFactory backfillFactory;
    private TaskEventService taskEventService;
    private AccountDao accountDao;
    private StudyService studyService;
    private UserConsentDao userConsentDao;
    private HealthCodeService healthCodeService;
    
    @Autowired
    public void setBackfillFactory(BackfillRecordFactory backfillFactory) {
        this.backfillFactory = backfillFactory;
    }
    @Autowired
    public void setTaskEventService(TaskEventService taskEventService) {
        this.taskEventService = taskEventService;
    }
    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
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
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        for (Iterator<Account> i = accountDao.getAllAccounts(); i.hasNext();) {
            Account account = i.next();
            Study study = studyService.getStudy(account.getStudyIdentifier());
            HealthId mapping = healthCodeService.getMapping(account.getHealthId());
            String healthCode = mapping.getCode();
            
            UserConsent consent = userConsentDao.getUserConsent(healthCode, study.getStudyIdentifier());
            taskEventService.publishEvent(healthCode, consent);
            
            callback.newRecords(
                backfillFactory.createAndSave(task, study, account, "enrollment event created"));
        }        
    }

}
