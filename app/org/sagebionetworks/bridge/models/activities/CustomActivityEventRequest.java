package org.sagebionetworks.bridge.models.activities;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;

/** An API request to add a custom activity event. */
@JsonDeserialize(builder = CustomActivityEventRequest.Builder.class)
public final class CustomActivityEventRequest {
    private final String eventKey;
    private final DateTime timestamp;
    private final String answerValue;

    // Private constructor. Use builder.
    private CustomActivityEventRequest(String eventKey, DateTime timestamp, String answerValue) {
        this.eventKey = eventKey;
        this.timestamp = timestamp;
        this.answerValue = answerValue;
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
    
    public String getAnswerValue() {
        return answerValue;
    }

    /** Custom activity event request builder. */
    public static class Builder {
        private String eventKey;
        private DateTime timestamp;
        private String answerValue;

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
        
        public Builder withAnswerValue(String answerValue) {
            this.answerValue = answerValue;
            return this;
        }

        /** Builds a custom activity event request. */
        public CustomActivityEventRequest build() {
            return new CustomActivityEventRequest(eventKey, timestamp, answerValue);
        }
    }

    @Override
    public int hashCode() {
        // equals/hashCode not implemented on Joda DateTime, use string representation instead
        String ts1 = (timestamp == null) ? null : timestamp.toString();
        return Objects.hash(eventKey, ts1, answerValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CustomActivityEventRequest other = (CustomActivityEventRequest) obj;
        // equals/hashCode not implemented on Joda DateTime, use string representation instead
        String ts1 = (timestamp == null) ? null : timestamp.toString();
        String ts2 = (other.timestamp == null) ? null : other.timestamp.toString();
        return Objects.equals(eventKey, other.eventKey)
                && Objects.equals(ts1, ts2) 
                && Objects.equals(answerValue, other.answerValue);
    }
}
