package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study;

public interface StudyDao {

    boolean doesIdentifierExist(String identifier);
    
    Study getStudy(String identifier);
    
    List<Study> getStudies();
    
    Study createStudy(Study study);
    
    Study updateStudy(Study study);
    
    void deleteStudy(Study study);

    void deactivateStudy(String studyId);
}
