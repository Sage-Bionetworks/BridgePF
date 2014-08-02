package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.HealthId;

public interface HealthCodeService {

    HealthId create();

    String getHealthCode(String id);
}
