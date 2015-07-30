package org.sagebionetworks.bridge.models.studies;

public enum MimeType {
    
    TEXT("text/plain"),
    HTML("text/html"),
    PDF("application/pdf");
    
    private MimeType(String type){
        this.type = type;
    }
    
    private final String type;
    @Override public String toString() {
        return type;
    }
}
