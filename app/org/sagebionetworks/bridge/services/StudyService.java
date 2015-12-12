package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface StudyService {

    public Study getStudy(String identifier);
    
    public Study getStudy(StudyIdentifier studyId);

    public List<Study> getStudies();

    public Study createStudy(Study study);

    public Study updateStudy(Study study, boolean isAdminUpdate);

    public void deleteStudy(String identifier);
    
    /**
     * The number of participants are calculated as the set of all unique health codes across all 
     * subpopulations in the study. This value is not cached and it is expensive to perform; to 
     * check if enrollment is reached, use isStudyAtEnrollmentLimit().
     * @param studyIdentifier
     * @return
     * @see isStudyAtEnrollmentLimit
     */
    public long getNumberOfParticipants(StudyIdentifier studyIdentifier);

    /**
     * Has enrollment met or slightly exceeded the limit set for th study? This value is cached.
     * @param study
     * @return
     */
    public boolean isStudyAtEnrollmentLimit(Study study);
    
    /**
     * Increment the study enrollment if this user has signed their first consent in the study. 
     * This adjusts the cached value without recalculating the enrollment from scratch (an 
     * expensive operation).
     * @param study
     * @param user
     */
    public void incrementStudyEnrollment(Study study, User user);
    
    /**
     * Decrement the study enrollment if this user has withdrawn from their last consent to 
     * participate in the study. This adjusts the cached value without recalculating the 
     * enrollment from scratch (an expensive operation).
     * @param study
     * @param user
     */
    public void decrementStudyEnrollment(Study study, User user);
}
