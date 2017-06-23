package org.sagebionetworks.bridge.models.activities;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.models.BridgeEntity;

@JsonDeserialize(as = ActivityEventImpl.class)
public interface ActivityEvent extends BridgeEntity {

    String getHealthCode();

    String getEventId();

    String getAnswerValue();

    Long getTimestamp();
}
