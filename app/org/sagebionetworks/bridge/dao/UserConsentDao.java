package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.StudyConsent;

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
     * Get the user consent record that consents the user to this study.
     * @param healthCode
     * @param consent
     * @return
     */
    UserConsent getUserConsent(String healthCode, StudyConsent consent);

    /**
     * Returns a non-null consent signature, consisting of the signature name and birthdate. Throws
     * EntityNotFoundException if no consent signature is found.
     */
    ConsentSignature getConsentSignature(String healthCode, StudyConsent consent);
}
