package org.sagebionetworks.bridge.models.backfill;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;

@BridgeTypeName("BackfillRecord")
public interface BackfillRecord extends BridgeEntity {

    String getTaskId();

    long getTimestamp();

    JsonNode toJsonNode();
}
