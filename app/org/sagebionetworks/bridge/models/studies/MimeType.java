package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.json.MimeTypeSerializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Our use of mime type serialization appears to have been using values like "text" and 
 * "pdf" as strings rather than real mime types like "application/pdf", so deserialization 
 * of both values needs special handling. This enum is excepted in the LowercaseEnumModule 
 * so Jackson will use the static factory method here.
 */
@JsonSerialize(using = MimeTypeSerializer.class)
public enum MimeType {
    
    TEXT("text/plain"),
    HTML("text/html"),
    PDF("application/pdf");
    
    @JsonCreator
    public static MimeType fromString(String mimeType) {
        if (mimeType != null) {
            switch(mimeType.toLowerCase()) {
            case "text/plain":
            case "text":
            case "plain":
                return MimeType.TEXT;
            case "text/html":
            case "html":
                return MimeType.HTML;
            case "application/pdf":
            case "pdf":
                return MimeType.PDF;
            }
        }
        return null;
    }
    
    private MimeType(String type){
        this.type = type;
    }
    
    private final String type;
    @Override public String toString() {
        return type;
    }
}
