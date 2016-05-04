package org.sagebionetworks.bridge.services.backfill;

import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Iterator;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

/**
 * Looking for Stormpath accounts that have consent signatures, but no DynamoDB consent record. Count and attempt to
 * repair. It's unlikely we'll have an enrollment event from which we can extract a signedOn timestamp, but we'll look
 * for that or else fall back to the beginning of yesterday.
 */
@Component("consentAuditAndRepairBackfill")
public class ConsentAuditAndRepairBackfill extends AsyncBackfillTemplate {

    private AccountDao accountDao;
    private UserConsentDao userConsentDao;
    private ActivityEventService activityEventService;
    private StudyConsentDao studyConsentDao;
    private ParticipantOptionsService optionsService;
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    final void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    @Autowired
    final void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    @Autowired
    final void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    
    @Override
    int getLockExpireInSeconds() {
        return 60*60*5; // 5 hours 
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        long syntheticSignedOn = DateTime.now().minusDays(1).withTimeAtStartOfDay().getMillis(); // start of day before today.
        
        int repaired = 0;
        int total = 0;
        Iterator<Account> i = accountDao.getAllAccounts();
        while (i.hasNext()) {
            Account account = i.next();
            total++;
            callback.newRecords(getBackfillRecordFactory().createOnly(task, "Looking at: " + account.getId()));
            StudyIdentifier studyId = account.getStudyIdentifier();
            SubpopulationGuid subpopGuid = SubpopulationGuid.create(studyId.getIdentifier());
            
            ConsentSignature signature = account.getActiveConsentSignature(subpopGuid);
            if (signature != null) {
                
                String healthCode = getHealthCode(task, callback, account);
                if (healthCode != null) {
                    UserConsent consent = userConsentDao.getActiveUserConsent(healthCode, subpopGuid);
                    if (consent == null) {
                        
                        // User has signature but no consent record. Try to repair and report on it.
                        
                        StringBuilder sb = new StringBuilder("Repairing account '"+account.getId()+"'");
                        
                        DateTime enrollment = activityEventService.getActivityEventMap(healthCode).get("enrollment");
                        
                        // We have to assume the active consent, information about the consent that was signed is contained
                        // in the DDB record that is missing.
                        StudyConsent studyConsent = studyConsentDao.getActiveConsent(subpopGuid);
                        
                        if (studyConsent != null) {
                            long signedOn = getSignedOnDate(sb, enrollment, syntheticSignedOn);
                            
                            // Give consent
                            consent = userConsentDao.giveConsent(healthCode, subpopGuid, studyConsent.getCreatedOn(), signedOn);
                            repaired++;
                            
                            // Create an enrollment event
                            if (consent != null && enrollment == null){
                                activityEventService.publishEnrollmentEvent(healthCode, consent);
                            }
                            updateSharingScope(sb, studyId, healthCode);
                            
                            // Things we don't do: we don't increment the study enrollment (that will eventually be
                            // recalculated from the db), we don't send out email, and we don't update the user's
                            // session. If their sharing scope is NO_SHARING, we set it to limited, because we don't 
                            // know their original choice.                            
                            
                            callback.newRecords(getBackfillRecordFactory().createOnly(task, sb.toString()));
                        } else {
                            sb.append(", no study consent found for " + studyId.getIdentifier());
                        }
                    }
                }
                
            } else if (!account.getConsentSignatureHistory(subpopGuid).isEmpty()) {
                // This can happen because people withdraw from a study. It's okay, but interesting... we still wonder if this person
                // has a record.
                String healthCode = getHealthCode(task, callback, account);
                if (healthCode != null) {
                    UserConsent consent = userConsentDao.getActiveUserConsent(healthCode, subpopGuid);
                    if (consent == null) {
                        callback.newRecords(getBackfillRecordFactory().createOnly(task, "Consent signatures exist but no active signature and no DDB consent record (error state?): " + account.getId()));
                    } else {
                        callback.newRecords(getBackfillRecordFactory().createOnly(task, "Consent signatures exist but no active signature (expected withdrawn state): " + account.getId()));
                    }
                } else {
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, "Consent signatures exist but no healthCode (error state): " + account.getId()));    
                }
            }
        }
        callback.newRecords(getBackfillRecordFactory().createOnly(task, "Repaired "+repaired+" out of "+total+" records."));
        
    }
    private void updateSharingScope(StringBuilder sb, StudyIdentifier studyId, String healthCode) {
        SharingScope scope = optionsService.getOptions(healthCode).getEnum(SHARING_SCOPE, SharingScope.class);
        if (scope == null || scope == SharingScope.NO_SHARING) {
            sb.append(", setting sharing to " + SharingScope.SPONSORS_AND_PARTNERS);
            optionsService.setEnum(studyId, healthCode, SHARING_SCOPE, SharingScope.SPONSORS_AND_PARTNERS);    
        }
    }
    private long getSignedOnDate(StringBuilder sb, DateTime enrollment, long syntheticSignedOn) {
        long signedOn = syntheticSignedOn;
        if (enrollment != null) {
            signedOn = enrollment.getMillis();
            sb.append(", enrollment event on " + enrollment.getMillis());
        } else {
            sb.append(", no enrollment event for signedOn date, using " + syntheticSignedOn);
        }
        return signedOn;
    }
    
    /**
     * Because these records are retrieved through the iterators, we don't guarantee there are health codes 
     * (these methods can be removed at some point, they're only used in the backfills).
     */
    private String getHealthCode(BackfillTask task, BackfillCallback callback, Account account) {
        if (account.getHealthCode() != null) {
            return account.getHealthCode();
        }
        callback.newRecords(getBackfillRecordFactory().createOnly(task, "No health code found: " + account.getId()));
        return null;
    }
}
