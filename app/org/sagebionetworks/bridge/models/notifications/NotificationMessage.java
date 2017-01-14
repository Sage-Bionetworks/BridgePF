package org.sagebionetworks.bridge.models.notifications;

import java.util.Objects;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * For information on the push notification JSON structures that are sent to SNS, see:
 * http://docs.aws.amazon.com/sns/latest/dg/mobile-push-send-custommessage.html
 * 
 * Note: There are many more fields that we can and will provide once we have an end-to-end system working. So we have
 * introduced a builder in anticipation of expanding this model.
 */
@JsonDeserialize(builder=NotificationMessage.Builder.class)
public final class NotificationMessage implements BridgeEntity {

    /**
     * Very short rendition of the notification. For example, this value will be displayed on an Apple iWatch, where the
     * message is shown for a short time only.
     */
    private final String subject;
    
    /**
     * Full notification message. 
     */
    private final String message;
    
    NotificationMessage(String subject, String message) {
        this.subject = subject;
        this.message = message;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(subject, message);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        NotificationMessage other = (NotificationMessage)obj;
        return Objects.equals(subject, other.getSubject()) &&
               Objects.equals(message, other.getMessage());
    }
    
    @Override
    public String toString() {
        return String.format("NotificationMessage[subject=%s, message%s]", subject, message);
    }
    
    public static class Builder {
        private String subject;
        private String message;
        
        public Builder withSubject(String subject) {
            this.subject = subject;
            return this;
        }
        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }
        public NotificationMessage build() {
            return new NotificationMessage(subject, message);
        }
    }
}
