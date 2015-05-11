package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface UserConsentDao {

    /**
     * Gives consent to the specified study.
     * @param healthCode
     * @param consent
     * @return
     *      the consent record
     */
    UserConsent giveConsent(String healthCode, StudyConsent consent);

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
}
