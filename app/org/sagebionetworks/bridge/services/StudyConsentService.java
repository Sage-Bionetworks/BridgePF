package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;

public interface StudyConsentService {

    /**
     * Adds a new consent document to the study, and sets that consent document
     * as active.
     * 
     * @param caller
     *            the user calling the method
     * @param studyKey
     *            key associated with the study.
     * @param path
     *            path to the document "e.g. '/path/to/doc.html'"
     * @param minAge
     *            minimum age required to sign consent document
     * @return the added consent document of type StudyConsent.
     * @throws Exception
     *             if caller is not an admin
     */
    public StudyConsent addConsent(User caller, String studyKey, String path, int minAge);

    /**
     * Gets the currently active consent document for the study.
     * 
     * @param caller
     *            the user calling the method
     * @param studyKey
     *            key associated with the study.
     * @return the currently active StudyConsent.
     * @throws Exception
     *             if caller is not an admin
     */
    public StudyConsent getActiveConsent(User caller, String studyKey);

    /**
     * Get all added consent documents for the study.
     * 
     * @param caller
     *            the user calling the method
     * @param studyKey
     *            key associated with the study.
     * @return list of all consent documents associated with study
     * @throws Exception
     *             if caller is not an admin
     */
    public List<StudyConsent> getAllConsents(User caller, String studyKey);

    /**
     * Gets the consent document associated with the study created at the
     * specified timestamp.
     * 
     * @param caller
     *            the user calling the method
     * @param studyKey
     *            key associated with the study.
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the specified consent document
     * @throws Exception
     *             if caller is not an admin
     */
    public StudyConsent getConsent(User caller, String studyKey, long timestamp);

    /**
     * Set the specified consent document as active, and set the currently
     * active document as inactive.
     * 
     * @param caller
     *            the user calling the method
     * @param studyKey
     *            key associated with the study.
     * @param timestamp
     *            time the consent document was added to the database.
     * @throws Exception
     *             if caller is not an admin
     */
    public void activateConsent(User caller, String studyKey, long timestamp);

    /**
     * Deletes the specified consent document from the study. If this consent
     * document is currently active, it will throw an exception.
     * 
     * @param caller
     *            the user calling the method
     * @param studyKey
     *            key associated with the study.
     * @param timestamp
     *            time the consent document was added to the database.
     * @throws Exception
     *             if the specified consent is currently active.
     * @throws Exception
     *             if caller is not an admin
     */
    public void deleteConsent(User caller, String studyKey, long timestamp);

}
