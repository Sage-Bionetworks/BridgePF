package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;

public interface StudyConsentService {

    /**
     * Adds a new consent document to the study, and sets that consent document
     * as active.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @param form
     *            form filled out by researcher including the path to the
     *            consent document and the minimum age required to consent.
     * @return the added consent document of type StudyConsent along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView addConsent(String subpopGuid, StudyConsentForm form)
            throws BridgeServiceException;

    /**
     * Gets the currently active consent document for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return the currently active StudyConsent along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView getActiveConsent(String subpopGuid) throws BridgeServiceException;

    /**
     * Gets the most recently created consent document for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return the most recent StudyConsent along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView getMostRecentConsent(String subpopGuid) throws BridgeServiceException;
    
    /**
     * Get all added consent documents for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return list of all consent documents associated with study along with its document content
     * @throws BridgeServiceException
     */
    public List<StudyConsent> getAllConsents(String subpopGuid) throws BridgeServiceException;

    /**
     * Gets the consent document associated with the study created at the
     * specified timestamp.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the specified consent document along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView getConsent(String subpopGuid, long timestamp) throws BridgeServiceException;

    /**
     * Set the specified consent document as active, setting all other consent documents 
     * as inactive.
     *
     * @param study
     *            study for this consent
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the activated consent document along with its document content
     * @throws BridgeServiceException
     */
    public StudyConsentView publishConsent(Study study, String subpopGuid, long timestamp) throws BridgeServiceException;

}
