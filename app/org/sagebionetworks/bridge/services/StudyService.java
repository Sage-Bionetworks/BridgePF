package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface StudyService {

    public Study getStudy(String identifier);
    
    public Study getStudy(StudyIdentifier studyId);

    public List<Study> getStudies();

    public Study createStudy(Study study);

    public Study updateStudy(Study study);

    public void deleteStudy(String identifier);

}
