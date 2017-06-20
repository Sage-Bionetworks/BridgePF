package org.sagebionetworks.bridge.models.activities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by liujoshua on 6/19/2017.
 */
public class ActivityEventImpl implements ActivityEvent {
    private final String healthCode;
    private final String eventId;
    private final String answerValue;
    private final Long timestamp;

    @JsonCreator
    public ActivityEventImpl(@JsonProperty("healthCode") String healthCode, @JsonProperty("eventId") String eventId,
            @JsonProperty("answerValue") String answerValue, @JsonProperty("timestamp") Long
            timestamp) {
        this.healthCode = healthCode;
        this.eventId = eventId;
        this.answerValue = answerValue;
        this.timestamp = timestamp;
    }

    @Override public String getHealthCode() {
        return healthCode;
    }

    @Override public String getEventId() {
        return eventId;
    }

    @Override public String getAnswerValue() {
        return answerValue;
    }

    @Override public Long getTimestamp() {
        return timestamp;
    }
}
