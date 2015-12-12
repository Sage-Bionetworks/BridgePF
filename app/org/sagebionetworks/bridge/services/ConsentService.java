package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public interface ConsentService {

    /**
     * Get the user's active consent signature (a signature that has not been withdrawn).
     * @param study
     * @param subpopGuid
     * @param user
     * @return
     * @throws EntityNotFoundException if no consent exists
     */
    ConsentSignature getConsentSignature(Study study, SubpopulationGuid subpopGuid, User user);

    /**
     * Consent this user to research. User will be updated to reflect consent.
     * @param study
     * @param subpopGuid
     * @param user
     * @param consentSignature
     * @param sharingScope
     * @param sendEmail
     * @return
     * @throws EntityAlreadyExistsException
     *      if the user already has an active consent to participate in research
     * @throws StudyLimitExceededException
     *      if enrolling the user would exceed the study enrollment limit
     */
    User consentToResearch(Study study, SubpopulationGuid subpopGuid, User user, ConsentSignature consentSignature,
            SharingScope sharingScope, boolean sendEmail);
    
    /**
     * Get all the consent status objects for this user. From these, we determine if the user 
     * has consented to the right consents to have access to the study, whether or not those 
     * consents are up-to-date, etc.
     * @param context
     * @return
     */
    List<ConsentStatus> getConsentStatuses(ScheduleContext context);

    /**
     * Withdraw consent in this study. The withdrawal date is recorded and the user can no longer 
     * access any APIs that require consent, although the user's account (along with the history of 
     * the user's participation) will not be delted.
     * @param study 
     * @param subpopGuid
     * @param user
     * @param withdrawal
     * @param withdrewOn
     */
    void withdrawConsent(Study study, SubpopulationGuid subpopGuid, User user, Withdrawal withdrawal, long withdrewOn);
    
    /**
     * Get a history of all consent records, whether withdrawn or not, including information from the 
     * consent signature and user consent records. The information is sufficient to identify the 
     * consent that exists for a healthCode, and to retrieve the version of the consent the participant 
     * signed to join the study.
     * @param study
     * @param subpopGuid
     * @param user
     */
    List<UserConsentHistory> getUserConsentHistory(Study study, SubpopulationGuid subpopGuid, User user);

    /**
     * Email the participant's signed consent agreement to the user's email address.
     * @param study
     * @param subpopGuid
     * @param user
     */
    void emailConsentAgreement(Study study, SubpopulationGuid subpopGuid, User user);
    
    /**
     * Delete all consent records, withdrawn or active, in the process of deleting a user account. This is 
     * used for tests, do not call this method to withdraw a user from a study, or we will not have auditable 
     * records about their participation.
     * @param study
     * @param user
     */
    void deleteAllConsentsForUser(Study study, User user);
}
