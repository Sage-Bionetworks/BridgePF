package org.sagebionetworks.bridge.models.activities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by jyliu on 6/12/2017.
 */
public class CustomActivityEventRequest {
    private final String eventKey;
    private final Long timestamp;

    @JsonCreator
    public CustomActivityEventRequest(@JsonProperty("eventKey") String eventKey,
            @JsonProperty("timestamp") Long timestamp) {
        this.eventKey = eventKey;
        this.timestamp = timestamp;
    }


    public String getEventKey() {
        return eventKey;
    }

    public Long getTimestamp() {
        return timestamp;
    }
}
