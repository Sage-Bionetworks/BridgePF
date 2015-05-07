package org.sagebionetworks.bridge.models.backfill;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@BridgeTypeName("BackfillTask")
public interface BackfillTask extends BridgeEntity {

    String getId();

    long getTimestamp();

    String getName();

    String getUser();

    String getStatus();
}
