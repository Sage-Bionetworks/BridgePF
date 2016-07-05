package org.sagebionetworks.bridge.services.backfill;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyService;

@Component("consentCreatedOnBackfill")
public class ConsentCreatedOnBackfill extends AsyncBackfillTemplate {

    private StudyService studyService;
    private AccountDao accountDao;
    private StudyConsentDao studyConsentDao;
    private UserConsentDao userConsentDao;
    
    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    @Autowired
    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }

    @Override
    int getLockExpireInSeconds() {
        return 60*60*5; 
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        /*
        List<Study> studies = studyService.getStudies();
        for (Study study : studies) {
            backfillStudy(task, callback, study);
        }
        */
        Study study = studyService.getStudy("api");
        backfillStudy(task, callback, study);
    }
    
    private void backfillStudy(BackfillTask task, BackfillCallback callback, Study study) {
        callback.newRecords(getBackfillRecordFactory().createOnly(task, "Examining study " + study.getIdentifier() + "..."));
        
        Iterator<AccountSummary> summaries = accountDao.getStudyAccounts(study);
        while(summaries.hasNext()) {
            AccountSummary summary = summaries.next();

            Account account = accountDao.getAccount(study, summary.getId());
            if (account == null) {
                callback.newRecords(getBackfillRecordFactory().createOnly(task, "Account " + summary.getId() + " not found."));
            } else if (processAccount(task, callback, study, account)) {
                accountDao.updateAccount(account);
                callback.newRecords(getBackfillRecordFactory().createOnly(task, "Account " + summary.getId() + " updated."));
            }
        }
    }

    private boolean processAccount(BackfillTask task, BackfillCallback callback, Study study, Account account) {
        boolean accountUpdated = false;

        Map<SubpopulationGuid,List<ConsentSignature>> map = account.getAllConsentSignatureHistories();
        for (SubpopulationGuid guid : map.keySet()) {
            
            List<ConsentSignature> list = map.get(guid);
            for (int i=0; i < list.size(); i++) {
                ConsentSignature existingSig = list.get(i);
                ConsentSignature updatedSig = fixConsentCreatedOn(task, callback, guid, account, existingSig);
                
                // BTW given the signature constructor, just instantiating a signature means it will gain a signed
                // on timestamp if it doesn't exist. So that's repaired by this as well.
                if (!existingSig.equals(updatedSig)) {
                    list.set(i, updatedSig);
                    accountUpdated = true;
                }
            }
        }
        return accountUpdated;
    }
    
    private ConsentSignature fixConsentCreatedOn(BackfillTask task, BackfillCallback callback, SubpopulationGuid guid, Account account,
            ConsentSignature signature) {
        if (signature.getConsentCreatedOn() == 0L) {
            try {
                UserConsent consent = userConsentDao.getUserConsent(account.getHealthCode(), guid, signature.getSignedOn());
                signature = new ConsentSignature.Builder().withConsentSignature(signature)
                        .withConsentCreatedOn(consent.getConsentCreatedOn()).build();
            } catch(EntityNotFoundException e) {
                // get what is likely to be the consent, the last saved consent, and use that.
                StudyConsent studyConsent = studyConsentDao.getActiveConsent(guid);
                if (studyConsent != null) {
                    signature = new ConsentSignature.Builder().withConsentSignature(signature)
                            .withConsentCreatedOn(studyConsent.getCreatedOn()).build();
                } else {
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, "StudyConsent not found for study"));
                }
            }
        }
        return signature;
    }
}
