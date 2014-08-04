package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.StudyConsent;

public interface StudyConsentDao {

    /**
     * Adds a consent to the study. Note the consent is added as
     * inactive. Must explicitly set it active.
     */
    StudyConsent addConsent(String studyKey, String path, int minAge);

    /**
     * Sets the consent active.
     */
    void setActive(StudyConsent studyConsent);

    /**
     * Gets the latest, active consent.
     */
    StudyConsent getConsent(String studyKey);

    /**
     * Gets the consent, activate or inactive, of the specified timestamp.
     */
    StudyConsent getConsent(String studyKey, long timestamp);

    /**
     * Gets all the consents, active and inactive, in reverse order of the timestamp, of a particular study.
     */
    List<StudyConsent> getConsents(String studyKey);
}
