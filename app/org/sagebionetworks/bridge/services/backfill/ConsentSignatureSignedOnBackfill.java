package org.sagebionetworks.bridge.services.backfill;

import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.StudyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;

@Component("signedOnBackfill")
public class ConsentSignatureSignedOnBackfill extends AsyncBackfillTemplate {

    private AccountDao accountDao;
    private StudyService studyService;
    private UserConsentDao userConsentDao;
    private HealthCodeService healthCodeService;
    private SortedMap<Integer, BridgeEncryptor> encryptors = Maps.newTreeMap();

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

    @Resource(name = "encryptorList")
    public void setEncryptors(List<BridgeEncryptor> list) {
        for (BridgeEncryptor encryptor : list) {
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
            if (mapping != null) {
                String healthCode = mapping.getCode();

                ConsentSignature sig = account.getConsentSignature();
                if (sig != null) {
                    UserConsent consent = userConsentDao.getUserConsent(healthCode, account.getStudyIdentifier());
                    if (consent != null) {
                        Study study = studyService.getStudy(account.getStudyIdentifier());
                        sig = new ConsentSignature.Builder().withConsentSignature(sig, consent.getSignedOn()).build();
                        account.setConsentSignature(sig);

                        accountDao.updateAccount(study, account);
                        callback.newRecords(getBackfillRecordFactory().createAndSave(task, study, account,
                                "account updated with signature"));
                    } else {
                        callback.newRecords(getBackfillRecordFactory().createOnly(task,
                                "user consent not found (signature not updated)"));
                    }
                } else {
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, "signature not found"));
                }
            } else {
                callback.newRecords(getBackfillRecordFactory().createOnly(task, "Health code record not found"));
            }
        }
    }

}
