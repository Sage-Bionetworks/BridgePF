package org.sagebionetworks.bridge.services;

import java.util.Collection;
import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Study2;

public interface StudyService {
    
    public Study getStudyByIdentifier(String key);
    
    public Study getStudyByHostname(String hostname);
    
    public Collection<Study> getStudies();
    
    public Study2 getStudy2ByIdentifier(String identifier);
    
    public Study2 getStudy2ByHostname(String hostname);
    
    public List<Study2> getStudies2();
    
    public Study2 createStudy(Study2 study);
    
    public Study2 updateStudy(Study2 study);
    
    public void deleteStudy(String identifier);
    
    public Study2 changeStudyId(String oldIdentifier, String newIdentifier);
}
