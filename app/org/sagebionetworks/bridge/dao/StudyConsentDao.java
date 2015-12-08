package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.studies.StudyConsent;

public interface StudyConsentDao {

    /**
     * Adds a consent to the study. Must provide either a path to a filesystem resource (deprecated), or the 
     * name of an S3 bucket as the storagePath for the document content. Note the consent is added as
     * inactive. Must explicitly set it active.
     * @param subpopGuid
     * @param storagePath
     * @param createdOn
     */
    StudyConsent addConsent(String subpopGuid, String storagePath, DateTime createdOn);

    /**
     * Set this consent to be the one and only activate consent record.
     * @param studyConsent
     * @return
     */
    StudyConsent publish(StudyConsent studyConsent);
    
    /**
     * Gets the active consent.
     * @param subpopGuid
     */
    StudyConsent getActiveConsent(String subpopGuid);

    /**
     * Gets the most recent consent (active or not).
     * @param subpopGuid
     */
    StudyConsent getMostRecentConsent(String subpopGuid);
    
    /**
     * Gets the consent, activate or inactive, of the specified timestamp.
     * @param subpopGuid
     * @param timestamp
     */
    StudyConsent getConsent(String subpopGuid, long timestamp);

    /**
     * Delete all the consents for a study. Only call when deleting a study.
     * @param subpopGuid
     */
    void deleteAllConsents(String subpopGuid);
    
    /**
     * Gets all the consents, active and inactive, in reverse order of the timestamp, of a particular study.
     * @param subpopGuid
     */
    List<StudyConsent> getConsents(String subpopGuid);
}
