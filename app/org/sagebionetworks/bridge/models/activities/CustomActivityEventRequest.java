package org.sagebionetworks.bridge.models.activities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

/**
 * Created by jyliu on 6/12/2017.
 */
public class CustomActivityEventRequest {
    private final String eventKey;
    private final DateTime timestamp;

    @JsonCreator
    public CustomActivityEventRequest(@JsonProperty("eventKey") String eventKey,
            @JsonProperty("timestamp") DateTime timestamp) {
        this.eventKey = eventKey;
        this.timestamp = timestamp;
    }
    public String getEventKey() {
        return eventKey;
    }

    public DateTime getTimestamp() {
        return timestamp;
    }
}
