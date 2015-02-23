package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface HealthCodeService {

    public HealthId createMapping(StudyIdentifier studyIdentifier);

    public HealthId getMapping(String healthId);
    
}
