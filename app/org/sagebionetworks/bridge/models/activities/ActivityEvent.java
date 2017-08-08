package org.sagebionetworks.bridge.models.activities;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;

@JsonDeserialize(as = DynamoActivityEvent.class)
public interface ActivityEvent extends BridgeEntity {
    ObjectWriter ACTIVITY_EVENT_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter",
                    SimpleBeanPropertyFilter.serializeAllExcept("healthCode")));

    String getHealthCode();

    String getEventId();

    String getAnswerValue();

    Long getTimestamp();
}
