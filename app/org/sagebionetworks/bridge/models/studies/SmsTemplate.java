package org.sagebionetworks.bridge.models.studies;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SmsTemplate {
    private final String message;
    
    @JsonCreator
    public SmsTemplate(@JsonProperty("message") String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(message);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SmsTemplate other = (SmsTemplate) obj;
        return Objects.equals(message, other.message);
    }

    @Override
    public String toString() {
        return String.format("SmsTemplate [message=%s]", message);
    }

}
