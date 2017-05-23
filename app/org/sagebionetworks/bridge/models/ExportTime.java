package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoExportTime;

@JsonDeserialize(as = DynamoExportTime.class)
public interface ExportTime extends BridgeEntity {
    static ExportTime create() {
        return new DynamoExportTime();
    }

    String getStudyId();
    void setStudyId(String studyId);

    Long getLastExportDateTime();
    void setLastExportDateTime(Long lastExportDateTime);
}
