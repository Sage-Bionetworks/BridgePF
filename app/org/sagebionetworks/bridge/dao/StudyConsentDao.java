package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public interface StudyConsentDao {

    /**
     * Adds a consent to the study. Must provide either a path to a filesystem resource (deprecated), or the 
     * name of an S3 bucket as the storagePath for the document content. Note the consent is added as
     * inactive. Must explicitly set it active.
     * @param subpopGuid
     * @param storagePath
     * @param createdOn
     */
    StudyConsent addConsent(SubpopulationGuid subpopGuid, String storagePath, DateTime createdOn);
    
    /**
     * Gets the most recent consent (active or not).
     * @param subpopGuid
     */
    StudyConsent getMostRecentConsent(SubpopulationGuid subpopGuid);
    
    /**
     * Gets the consent, activate or inactive, of the specified timestamp.
     * @param subpopGuid
     * @param timestamp
     */
    StudyConsent getConsent(SubpopulationGuid subpopGuid, long timestamp);

    /**
     * Delete all the consents for a study. Only call when deleting a study.
     * @param subpopGuid
     */
    void deleteAllConsents(SubpopulationGuid subpopGuid);
    
    /**
     * Gets all the consents, active and inactive, in reverse order of the timestamp, of a particular study.
     * @param subpopGuid
     */
    List<StudyConsent> getConsents(SubpopulationGuid subpopGuid);
}
