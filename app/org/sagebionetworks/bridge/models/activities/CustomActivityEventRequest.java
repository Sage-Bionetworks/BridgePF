package org.sagebionetworks.bridge.models.activities;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;

/** An API request to add a custom activity event. */
@JsonDeserialize(builder = CustomActivityEventRequest.Builder.class)
public class CustomActivityEventRequest {
    private final String eventKey;
    private final DateTime timestamp;

    // Private constructor. Use builder.
    private CustomActivityEventRequest(String eventKey, DateTime timestamp) {
        this.eventKey = eventKey;
        this.timestamp = timestamp;
    }

    /**
     * Activity event key. Bridge will automatically pre-pend "custom:" when forming the event ID (eg, event key
     * "studyBurstStart" becomes event ID "custom:studyBurstStart").
     */
    public String getEventKey() {
        return eventKey;
    }

    /** When the activity occurred. */
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getTimestamp() {
        return timestamp;
    }

    /** Custom activity event request builder. */
    public static class Builder {
        private String eventKey;
        private DateTime timestamp;

        /** @see CustomActivityEventRequest#getEventKey */
        public Builder withEventKey(String eventKey) {
            this.eventKey = eventKey;
            return this;
        }

        /** @see CustomActivityEventRequest#getTimestamp */
        @JsonDeserialize(using = DateTimeDeserializer.class)
        public Builder withTimestamp(DateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /** Builds a custom activity event request. */
        public CustomActivityEventRequest build() {
            return new CustomActivityEventRequest(eventKey, timestamp);
        }
    }
}
