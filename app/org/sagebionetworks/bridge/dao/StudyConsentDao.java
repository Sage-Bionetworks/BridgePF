package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public interface StudyConsentDao {

    /**
     * Adds a consent to the study.
     */
    StudyConsent addConsent(SubpopulationGuid subpopGuid, String storagePath, long createdOn);
    
    /**
     * Gets the most recent consent (consent with the most recent createdOn timestamp).
     */
    StudyConsent getMostRecentConsent(SubpopulationGuid subpopGuid);
    
    /**
     * Gets the consent created at the specified timestamp.
     */
    StudyConsent getConsent(SubpopulationGuid subpopGuid, long consentCreatedOn);

    /**
     * Delete all the consents for a subpopulation. Only call when physically deleting a subpopulation
     * (usually as part of a test).
     */
    void deleteAllConsents(SubpopulationGuid subpopGuid);
    
    /**
     * Gets all the consents in reverse order of the timestamp, for a particularly subpopulation.
     */
    List<StudyConsent> getConsents(SubpopulationGuid subpopGuid);
}
