package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.HealthCode;

public interface HealthCodeDao {
    boolean setIfNotExist(HealthCode code);
}
