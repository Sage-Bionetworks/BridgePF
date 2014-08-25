package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.StudyConsentForm;
import org.sagebionetworks.bridge.models.User;

public interface StudyConsentService {

    /**
     * Adds a new consent document to the study, and sets that consent document
     * as active.
     * 
     * @param caller
     *            the user calling the method.
     * @param studyKey
     *            key associated with the study.
     * @param form
     *            form filled out by researcher including the path to the
     *            consent document and the minimum age required to consent.
     * @return the added consent document of type StudyConsent.
     * @throws BridgeServiceException
     */
    public StudyConsent addConsent(User caller, String studyKey, StudyConsentForm form) throws BridgeServiceException;

    /**
     * Gets the currently active consent document for the study.
     * 
     * @param caller
     *            the user calling the method.
     * @param studyKey
     *            key associated with the study.
     * @return the currently active StudyConsent.
     * @throws BridgeServiceException
     */
    public StudyConsent getActiveConsent(User caller, String studyKey) throws BridgeServiceException;

    /**
     * Get all added consent documents for the study.
     * 
     * @param caller
     *            the user calling the method.
     * @param studyKey
     *            key associated with the study.
     * @return list of all consent documents associated with study.
     * @throws BridgeServiceException
     */
    public List<StudyConsent> getAllConsents(User caller, String studyKey) throws BridgeServiceException;

    /**
     * Gets the consent document associated with the study created at the
     * specified timestamp.
     * 
     * @param caller
     *            the user calling the method.
     * @param studyKey
     *            key associated with the study.
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the specified consent document.
     * @throws BridgeServiceException
     */
    public StudyConsent getConsent(User caller, String studyKey, long timestamp) throws BridgeServiceException;

    /**
     * Set the specified consent document as active, and set the currently
     * active document as inactive.
     * 
     * @param caller
     *            the user calling the method.
     * @param studyKey
     *            key associated with the study.
     * @param timestamp
     *            time the consent document was added to the database.
     * @throws BridgeServiceException
     */
    public void activateConsent(User caller, String studyKey, long timestamp) throws BridgeServiceException;

    /**
     * Deletes the specified consent document from the study. If this consent
     * document is currently active, it will throw an exception.
     * 
     * @param caller
     *            the user calling the method.
     * @param studyKey
     *            key associated with the study.
     * @param timestamp
     *            time the consent document was added to the database.
     * @throws Exception
     *             if the specified consent is currently active.
     * @throws BridgeServiceException
     */
    public void deleteConsent(User caller, String studyKey, long timestamp) throws BridgeServiceException;

}
