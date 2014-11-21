package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Tracker;

public interface StudyService {
    
    public Tracker getTrackerByIdentifier(String trackerId);
    
    public Study getStudyByIdentifier(String identifier);
    
    public Study getStudyByHostname(String hostname);
    
    public List<Study> getStudies();
    
    public Study createStudy(Study study);
    
    public Study updateStudy(Study study);
    
    public void deleteStudy(String identifier);
    
}
