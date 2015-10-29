package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface ConsentService {

    /**
     * Get the user's active consent signature (a signature that has not been withdrawn).
     * @param study
     * @param user
     * @return
     * @throws EntityNotFoundException if no consent exists
     */
    ConsentSignature getConsentSignature(Study study, User user);

    /**
     * Consent this user to research. User will be updated to reflect consent.
     * @param study
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
    User consentToResearch(Study study, User user, ConsentSignature consentSignature,
            SharingScope sharingScope, boolean sendEmail);

    /**
     * Has this user signed the current and active consent agreement?
     * @param studyIdentifier
     * @param user
     * @return true if the user has signed and they signed the currently active 
     *      study consent.
     */
    boolean hasUserSignedActiveConsent(StudyIdentifier studyIdentifier, User user);

    /**
     * Has this user consented to any version of the research consent agreement? As long as 
     * the user has consented to any version of the consent, they may access the APIs, though 
     * their session will report the fact that the consent has changed since the user signed.
     * @param studyIdentifier
     * @param user
     * @return
     */
    boolean hasUserConsentedToResearch(StudyIdentifier studyIdentifier, User user);

    /**
     * Withdraw consent in this study. The withdrawal date is recorded and the user can no longer 
     * access any APIs that require consent, although the user's account (along with the history of 
     * the user's participation) will not be delted. 
     * @param study
     * @param user
     * @param withdrawal
     */
    void withdrawConsent(Study study, User user, Withdrawal withdrawal);
    
    /**
     * Get a history of all consent records, whether withdrawn or not, including information from the 
     * consent signature and user consent records. The information is sufficient to identify the 
     * consent that exists for a healthCode, and to retrieve the version of the consent the participant 
     * signed to join the study.
     */
    List<UserConsentHistory> getUserConsentHistory(Study study, User user);

    /**
     * Email the participant's signed consent agreement to the user's email address.
     * @param study
     * @param user
     */
    void emailConsentAgreement(Study study, User user);

    /**
     * If an enrollment limit has been set, are the number of participants at or above that limit? This value 
     * will change as user's join or leave the study.
     * @param study
     * @return
     */
    boolean isStudyAtEnrollmentLimit(Study study);
    
    /**
     * Delete all consent records, withdrawn or active, in the process of deleting a user account. This is 
     * used for tests, do not call this method to withdraw a user from a study, or we will not have auditable 
     * records about their participation.
     * @param study
     * @param user
     */
    void deleteAllConsents(Study study, User user);
}
