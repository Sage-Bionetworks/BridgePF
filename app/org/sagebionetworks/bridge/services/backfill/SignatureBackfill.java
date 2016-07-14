package org.sagebionetworks.bridge.services.backfill;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;

@Component("signatureBackfill")
public class SignatureBackfill extends AsyncBackfillTemplate {
    
    private StudyService studyService;
    private AccountDao accountDao;
    private StudyConsentDao studyConsentDao;
    private SubpopulationService subpopulationService;
    
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
    public void setSubpopulationService(SubpopulationService subpopulationService) {
        this.subpopulationService = subpopulationService;
    }

    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }
    
    @Override
    void doBackfill(final BackfillTask task, BackfillCallback callback) {
        List<Study> studies = getStudies()
                .stream()
                .map(studyId -> studyService.getStudy(studyId))
                .collect(Collectors.toList());
        for (Study study : studies) {
            backfillStudy(task, callback, study);
        }
    }
    
    private List<Study> getStudies() {
        return studyService.getStudies();
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
                try {
                    accountDao.updateAccount(account);
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, "Account " + summary.getId() + " updated."));
                } catch(Exception e) {
                    e.printStackTrace();
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, "Exception saving account " + summary.getId()));
                }
            } else {
                callback.newRecords(getBackfillRecordFactory().createOnly(task, "."));
            }
        }
    }
    
    private boolean processAccount(BackfillTask task, BackfillCallback callback, Study study, Account account) {
        boolean accountUpdated = false;

        Map<SubpopulationGuid,List<ConsentSignature>> map = account.getAllConsentSignatureHistories();
        for (SubpopulationGuid guid : map.keySet()) {
            
            List<ConsentSignature> list = map.get(guid);
            for (int i=0; i < list.size(); i++) {
                try {
                    ConsentSignature existingSig = list.get(i);
                    ConsentSignature updatedSig = fixConsentCreatedOn(task, callback, study.getStudyIdentifier(), guid,
                            account, existingSig);
                    
                    if (!existingSig.equals(updatedSig)) {
                        list.set(i, updatedSig);
                        accountUpdated = true;
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, "Exception processing account " + account.getId() + " signature: " + e.getMessage()));
                    return false;
                }
            }
        }
        return accountUpdated;
    }
    
    private ConsentSignature fixConsentCreatedOn(BackfillTask task, BackfillCallback callback, StudyIdentifier studyId,
            SubpopulationGuid guid, Account account, ConsentSignature signature) {
        if (signature.getConsentCreatedOn() == 0L) {
            Subpopulation subpop = subpopulationService.getSubpopulation(studyId, guid);
            StudyConsent studyConsent = studyConsentDao.getConsent(guid, subpop.getPublishedConsentCreatedOn());
            if (studyConsent == null) {
                // Pretty much total disaster.
                callback.newRecords(getBackfillRecordFactory().createOnly(task, "StudyConsent not found for study. No update to " + account.getId() + " has been made."));
                return signature;
            }
            signature = new ConsentSignature.Builder().withConsentSignature(signature)
                    .withConsentCreatedOn(studyConsent.getCreatedOn())
                    .withSignedOn(account.getCreatedOn().getMillis())
                    .build();
        }
        return signature;
    }    
}
