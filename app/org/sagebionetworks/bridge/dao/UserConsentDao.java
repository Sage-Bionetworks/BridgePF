package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.StudyConsent;

public interface UserConsentDao {

    /**
     * Gives consent to the specified study.
     */
    void giveConsent(String healthCode, StudyConsent studyConsent);

    /**
     * Withdraws consent to the specified study.
     */
    void withdrawConsent(String healthCode, StudyConsent studyConsent);

    /**
     * Whethe the user has consented to the specified study.
     */
    boolean hasConsented(String healthCode, StudyConsent studyConsent);
}
