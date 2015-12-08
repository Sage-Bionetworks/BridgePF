package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface StudyService {

    public Study getStudy(String identifier);
    
    public Study getStudy(StudyIdentifier studyId);

    public List<Study> getStudies();

    public Study createStudy(Study study);

    public Study updateStudy(Study study, boolean isAdminUpdate);

    public void deleteStudy(String identifier);
    
    public long getNumberOfParticipants(StudyIdentifier studyIdentifier);

    /**
     * If an enrollment limit has been set, are the number of participants at or above that limit? This value 
     * will change as user's join or leave the study. This is the sum of all unique health codes across all 
     * subpopulations for this study.
     * @param study
     * @return
     */
    boolean isStudyAtEnrollmentLimit(Study study);
    
    public void incrementStudyEnrollment(Study study) throws StudyLimitExceededException;
    
    public void decrementStudyEnrollment(Study study);
}
