package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface StudyConsentService {

    /**
     * Adds a new consent document to the study, and sets that consent document
     * as active.
     *
     * @param studyIdentifier
     *            key associated with the study.
     * @param form
     *            form filled out by researcher including the path to the
     *            consent document and the minimum age required to consent.
     * @return the added consent document of type StudyConsent along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView addConsent(StudyIdentifier studyIdentifier, StudyConsentForm form)
            throws BridgeServiceException;

    /**
     * Gets the currently active consent document for the study.
     *
     * @param studyIdentifier
     *            key associated with the study.
     * @return the currently active StudyConsent along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView getActiveConsent(StudyIdentifier studyIdentifier) throws BridgeServiceException;

    /**
     * Gets the most recently created consent document for the study.
     *
     * @param studyIdentifier
     *            key associated with the study.
     * @return the most recent StudyConsent along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView getMostRecentConsent(StudyIdentifier studyIdentifier) throws BridgeServiceException;
    
    /**
     * Get all added consent documents for the study.
     *
     * @param studyIdentifier
     *            key associated with the study.
     * @return list of all consent documents associated with study along with its document content
     * @throws BridgeServiceException
     */
    public List<StudyConsent> getAllConsents(StudyIdentifier studyIdentifier) throws BridgeServiceException;

    /**
     * Gets the consent document associated with the study created at the
     * specified timestamp.
     *
     * @param studyIdentifier
     *            key associated with the study.
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the specified consent document along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView getConsent(StudyIdentifier studyIdentifier, long timestamp) throws BridgeServiceException;

    /**
     * Set the specified consent document as active, setting all other consent documents 
     * as inactive.
     *
     * @param studyIdentifier
     *            key associated with the study.
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the activated consent document along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView activateConsent(StudyIdentifier studyIdentifier, long timestamp) throws BridgeServiceException;

}
