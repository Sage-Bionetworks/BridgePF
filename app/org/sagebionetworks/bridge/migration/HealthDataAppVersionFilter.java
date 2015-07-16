package org.sagebionetworks.bridge.migration;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

import com.fasterxml.jackson.databind.JsonNode;

class HealthDataAppVersionFilter implements HealthDataFilter {

    private final String appVersionNumber;

    HealthDataAppVersionFilter(final String appVersionNumber) {
        checkNotNull(appVersionNumber);
        this.appVersionNumber = appVersionNumber;
    }

    @Override
    public boolean keep(HealthDataRecord record) {
        if (record != null) {
            final JsonNode node = record.getMetadata();
            if (node != null) {
                final JsonNode appVersion = node.get("appVersion");
                if (appVersion != null && appVersion.asText().contains(appVersionNumber)) {
                    return true;
                }
            }
        }
        return false;
    }
}
