package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study;

public interface StudyDao {

    public boolean doesIdentifierExist(String identifier);
    
    public Study getStudy(String identifier);
    
    public List<Study> getStudies();
    
    public Study createStudy(Study study);
    
    public Study updateStudy(Study study);
    
    public void deleteStudy(Study study);
    
}
