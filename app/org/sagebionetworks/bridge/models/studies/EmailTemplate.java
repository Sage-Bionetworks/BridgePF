package org.sagebionetworks.bridge.models.studies;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The content describing an email template that will be user for workflow related to account 
 * management (email verification, reset password emails). For a list of all the template 
 * variables that can be used in these templates, see the API documentation.
 */
public final class EmailTemplate {
    
    private final String subject;
    private final MimeType mimeType;
    private final String body;
    
    @JsonCreator
    public EmailTemplate(@JsonProperty("subject") String subject, @JsonProperty("body") String body,
                    @JsonProperty("mimeType") MimeType mimeType) {
        this.subject = subject;
        this.mimeType = (mimeType == null) ? MimeType.HTML : mimeType;
        this.body = body;
    }
    
    public String getSubject() {
        return subject;
    }
    public String getBody() {
        return body;
    }
    public MimeType getMimeType() {
        return mimeType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(body);
        result = prime * result + Objects.hashCode(subject);
        result = prime * result + Objects.hashCode(mimeType);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        EmailTemplate other = (EmailTemplate) obj;
        return (Objects.equals(body, other.body) && Objects.equals(subject, other.subject) && 
                Objects.equals(mimeType, other.mimeType));
    }

    @Override
    public String toString() {
        return String.format("EmailTemplate [subject=%s, body=%s, mimeType=%s]", subject, body, mimeType);
    }
    
}
