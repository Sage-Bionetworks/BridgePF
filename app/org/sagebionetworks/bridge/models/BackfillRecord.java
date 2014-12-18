package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.BridgeTypeName;

@BridgeTypeName("BackfillRecord")
public interface BackfillRecord extends BridgeEntity {

    String getTaskId();

    long getTimestamp();

    String getRecord();
}
