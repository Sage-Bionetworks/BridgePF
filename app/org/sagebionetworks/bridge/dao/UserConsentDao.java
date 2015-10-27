package org.sagebionetworks.bridge.dao;

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
     * @return
     */
    boolean withdrawConsent(String healthCode, StudyIdentifier studyIdentifier);

    /**
     * Whether the user has consented to the specified study.
     * @param healthCode
     * @param studyIdentifier
     * @return
     */
    boolean hasConsented(String healthCode, StudyIdentifier studyIdentifier);

    /**
     * Get the user consent record that consents the user to this study.
     * @param healthCode
     * @param studyIdentifier
     * @return
     */
    UserConsent getUserConsent(String healthCode, StudyIdentifier studyIdentifier);

    /**
     * @param studyIdentifier
     * @return
     */
    long getNumberOfParticipants(StudyIdentifier studyIdentifier);
    
    /**
     * Delete all consent records for a user, in order to clean up after tests. If withdrawing a user, 
     * call <code>withDrawConsent()</code> instead.
     */
    void deleteConsentRecords(String healthCode, StudyIdentifier studyIdentifier);
    
    /**
     * Copy existing consent record over from consent 2 table to consent 3 table.
     * @param healthCode
     * @param studyIdentifier
     */
    boolean migrateConsent(String healthCode, StudyIdentifier studyIdentifier);
}
