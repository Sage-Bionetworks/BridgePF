package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.StudyConsent;

public interface UserConsentDao {

    /**
     * Gives consent to the specified study.
     */
    void giveConsent(String healthCode, StudyConsent consent, ConsentSignature consentSignature);

    /**
     * Withdraws consent to the specified study.
     */
    void withdrawConsent(String healthCode, StudyConsent consent);

    /**
     * Gets the time stamp of the consent document the user has consented.
     * Returns null if the user has not consented yet.
     */
    Long getConsentCreatedOn(String healthCode, String studyKey);

    /**
     * Whether the user has consented to the specified study.
     */
    boolean hasConsented(String healthCode, StudyConsent consent);

    /**
     * Resume sharing data for the study.
     */
    void resumeSharing(String healthCode, StudyConsent consent);

    /**
     * Stop sharing data for the study.
     */
    void suspendSharing(String healthCode, StudyConsent consent);

    /**
     * Whether the user has agreed to share data for the study.
     */
    boolean isSharingData(String healthCode, StudyConsent consent);

    /**
     * Returns the consent signature, consisting of the signature name and birthdate.
     */
    ConsentSignature getConsentSignature(String healthCode, StudyConsent consent);
}
