package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface UserConsentDao {

    /**
     * Gives consent to the specified study.
     * @param healthCode
     * @param consent
     * @param signedOn
     * @return
     *      the consent record
     */
    UserConsent giveConsent(String healthCode, StudyConsent consent, long signedOn);

    /**
     * Withdraws consent to the specified study.
     * @param healthCode
     * @param studyIdentifier
     * @param withdrewOn
     * @return
     */
    void withdrawConsent(String healthCode, StudyIdentifier studyIdentifier, long withdrewOn);

    /**
     * Whether the user has consented to the specified study.
     * @param healthCode
     * @param studyIdentifier
     * @return
     */
    boolean hasConsented(String healthCode, StudyIdentifier studyIdentifier);

    /**
     * Get the active user consent record (a consent that has not been withdrawn), 
     * that consents the user to this study.
     * @param healthCode
     * @param studyIdentifier
     * @return
     */
    UserConsent getActiveUserConsent(String healthCode, StudyIdentifier studyIdentifier);

    /**
     * Get a specific consent signature by the signing date. These are retrieved when reconstructing 
     * the users consent history.
     * @param healthCode
     * @param studyIdentifier
     * @param signedOn
     * @return
     */
    UserConsent getUserConsent(String healthCode, StudyIdentifier studyIdentifier, long signedOn);
    
    /**
     * Get the entire history of user consent records (including withdrawn consents, if any).
     * @param healthCode
     * @param studyIdentifier
     * @return
     */
    List<UserConsent> getUserConsentHistory(String healthCode, StudyIdentifier studyIdentifier);
    
    /**
     * @param studyIdentifier
     * @return
     */
    long getNumberOfParticipants(StudyIdentifier studyIdentifier);
    
    /**
     * Delete all consent records for a user, in order to clean up after tests. If withdrawing a user, 
     * call <code>withDrawConsent()</code> instead.
     */
    void deleteAllConsents(String healthCode, StudyIdentifier studyIdentifier);
}
