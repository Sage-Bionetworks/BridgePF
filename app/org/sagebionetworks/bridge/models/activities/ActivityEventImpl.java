package org.sagebionetworks.bridge.models.activities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;

/**
 * Created by liujoshua on 6/19/2017.
 */
public class ActivityEventImpl implements ActivityEvent {
    private final String healthCode;
    private final String eventId;
    private final String answerValue;
    private final Long timestamp;

    @JsonCreator
    public ActivityEventImpl(@JsonProperty("eventId") String eventId,
            @JsonProperty("answerValue") String answerValue, @JsonProperty("timestamp") Long
            timestamp) {
        this(null, eventId, answerValue, timestamp);
    }

    public ActivityEventImpl(String healthCode, String eventId, String answerValue, Long timestamp) {
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

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override public Long getTimestamp() {
        return timestamp;
    }
}
