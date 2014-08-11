package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.StudyConsent;

public interface UserConsentDao {

    /**
     * Gives consent to the specified study.
     */
    void giveConsent(String healthCode, StudyConsent studyConsent, ResearchConsent researchConsent);

    /**
     * Withdraws consent to the specified study.
     */
    void withdrawConsent(String healthCode, StudyConsent studyConsent);

    /**
     * Whether the user has consented to the specified study.
     */
    boolean hasConsented(String healthCode, StudyConsent studyConsent);
    
    /**
     * Returns the consent signature, consisting of the signature name and birthdate.
     */
    ResearchConsent getConsentSignature(String healthCode, StudyConsent studyConsent);
}
