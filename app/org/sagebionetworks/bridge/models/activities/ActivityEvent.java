package org.sagebionetworks.bridge.models.activities;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface ActivityEvent extends BridgeEntity {

    String getHealthCode();

    String getEventId();

    String getAnswerValue();

    Long getTimestamp();
}
