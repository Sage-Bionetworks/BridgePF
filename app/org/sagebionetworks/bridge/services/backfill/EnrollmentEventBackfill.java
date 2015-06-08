package org.sagebionetworks.bridge.services.backfill;

import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.TaskEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;

@Component("enrollmentEventBackfill")
public class EnrollmentEventBackfill extends AsyncBackfillTemplate {
    private AccountDao accountDao;
    private TaskEventService taskEventService;
    private UserConsentDao userConsentDao;
    private HealthCodeService healthCodeService;
    private SortedMap<Integer,Encryptor> encryptors = Maps.newTreeMap();

    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    public void setTaskEventService(TaskEventService taskEventService) {
        this.taskEventService = taskEventService;
    }
    @Autowired
    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    @Autowired
    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    @Resource(name="encryptorList")
    public void setEncryptors(List<Encryptor> list) {
        for (Encryptor encryptor : list) {
            encryptors.put(encryptor.getVersion(), encryptor);
        }
    }

    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        callback.newRecords(getBackfillRecordFactory().createOnly(task, "Starting to examine accounts"));
        
        Iterator<Account> i = accountDao.getAllAccounts();
        while (i.hasNext()) {
            Account account = i.next();
            
            callback.newRecords(getBackfillRecordFactory().createOnly(task, "Examining account: " + account.getId()));
            HealthId mapping = healthCodeService.getMapping(account.getHealthId());
            UserConsent consent = null;
            if (mapping != null) {
                String healthCode = mapping.getCode();

                Study study = new DynamoStudy();
                study.setIdentifier(account.getStudyIdentifier().getIdentifier());
                
                consent = userConsentDao.getUserConsent(healthCode, study.getStudyIdentifier());
                if (consent != null) {
                    taskEventService.publishEvent(healthCode, consent);
                    callback.newRecords(
                        getBackfillRecordFactory().createAndSave(task, study, account, "enrollment event created"));
                }
            } 
            if (mapping == null && consent == null) {
                callback.newRecords(getBackfillRecordFactory().createOnly(task, "Health code and consent record not found"));
            } else if (mapping == null) {
                callback.newRecords(getBackfillRecordFactory().createOnly(task, "Health code not found"));
            } else if (consent == null) {
                callback.newRecords(getBackfillRecordFactory().createOnly(task, "Consent record not found"));
            }
        }
    }
}
