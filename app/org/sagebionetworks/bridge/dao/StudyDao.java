package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study2;

public interface StudyDao {

    public boolean doesIdentifierExist(String identifier);
    
    public Study2 getStudy(String identifier);
    
    public List<Study2> getStudies();
    
    public Study2 createStudy(Study2 study);
    
    public Study2 updateStudy(Study2 study);
    
    public void deleteStudy(String identifier);
    
}
