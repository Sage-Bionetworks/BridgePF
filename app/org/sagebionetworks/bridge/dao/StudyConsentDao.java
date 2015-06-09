package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface StudyConsentDao {

    /**
     * Adds a consent to the study. Must provide either a path to a filesystem resource (deprecated), or the 
     * name of an S3 bucket as the storagePath for the document content. Note the consent is added as
     * inactive. Must explicitly set it active.
     */
    StudyConsent addConsent(StudyIdentifier studyIdentifier, String path, String storagePath, DateTime createdOn);

    /**
     * Set this consent to be the one and only activate consent record.
     * @param consent
     * @return
     */
    StudyConsent activateConsent(StudyConsent studyConsent);
    
    /**
     * Set this consent to be the one and only activate consent record.
     * @param consent
     * @return
     */
    StudyConsent deactivateConsent(StudyConsent studyConsent);
    
    /**
     * Sets the consent active or inactive, depending on the boolean flag.
     */
    //StudyConsent setActive(StudyConsent studyConsent, boolean active);

    /**
     * Gets the latest, active consent.
     */
    StudyConsent getConsent(StudyIdentifier studyIdentifier);

    /**
     * Gets the consent, activate or inactive, of the specified timestamp.
     */
    StudyConsent getConsent(StudyIdentifier studyIdentifier, long timestamp);
    
    /**
     * Deletes the consent of the specified timestamp.
     */
    void deleteConsent(StudyIdentifier studyIdentifier, long timestamp);

    /**
     * Gets all the consents, active and inactive, in reverse order of the timestamp, of a particular study.
     */
    List<StudyConsent> getConsents(StudyIdentifier studyIdentifier);
}
