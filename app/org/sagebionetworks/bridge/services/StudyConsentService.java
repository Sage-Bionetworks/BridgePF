package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

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
     */
    public StudyConsentView addConsent(SubpopulationGuid subpopGuid, StudyConsentForm form);

    /**
     * Gets the currently active consent document for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return the currently active StudyConsent along with its document content
     */
    public StudyConsentView getActiveConsent(SubpopulationGuid subpopGuid);

    /**
     * Gets the most recently created consent document for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return the most recent StudyConsent along with its document content
     */
    public StudyConsentView getMostRecentConsent(SubpopulationGuid subpopGuid);
    
    /**
     * Get all added consent documents for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return list of all consent documents associated with study along with its document content
     */
    public List<StudyConsent> getAllConsents(SubpopulationGuid subpopGuid);

    /**
     * Gets the consent document associated with the study created at the
     * specified timestamp.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the specified consent document along with its document content
     */
    public StudyConsentView getConsent(SubpopulationGuid subpopGuid, long timestamp);

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
     */
    public StudyConsentView publishConsent(Study study, SubpopulationGuid subpopGuid, long timestamp);

}
