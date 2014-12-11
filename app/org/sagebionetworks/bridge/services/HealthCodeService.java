package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;

public interface HealthCodeService {

    HealthId create(Study study);

    String getHealthCode(String id);

    String getStudyIdentifier(String healthCode);
}
