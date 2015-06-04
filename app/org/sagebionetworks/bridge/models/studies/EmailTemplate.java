package org.sagebionetworks.bridge.models.studies;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class EmailTemplate {

    private final String subject;
    private final String body;
    
    @JsonCreator
    public EmailTemplate(@JsonProperty("subject") String subject, @JsonProperty("body") String body) {
        this.subject = subject;
        this.body = body;
    }
    
    public String getSubject() {
        return subject;
    }
    public String getBody() {
        return body;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(body);
        result = prime * result + Objects.hashCode(subject);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        EmailTemplate other = (EmailTemplate) obj;
        return (Objects.equals(body,  other.body) && Objects.equals(subject, other.subject));
    }

    @Override
    public String toString() {
        return String.format("EmailTemplate [subject=%s, body=%s]", subject, body);
    }
    
}
