package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.HealthId;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface HealthCodeService {

    HealthId create(StudyIdentifier studyIdentifier);

    String getHealthCode(String healthId);

    // Never used anywhere... still in the DAO though.
    // String getStudyIdentifier(String healthCode);
}
