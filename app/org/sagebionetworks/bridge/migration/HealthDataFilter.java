package org.sagebionetworks.bridge.migration;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

public interface HealthDataFilter {

    boolean keep(HealthDataRecord record);
}
