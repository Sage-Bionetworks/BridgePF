package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.BridgeTypeName;

@BridgeTypeName("BackfillTask")
public interface BackfillTask extends BridgeEntity {

    String getId();

    long getTimestamp();

    String getName();

    String getDescription();

    String getUser();

    String getStatus();
}
