package org.sagebionetworks.bridge.models;

public class StatusMessage {

    private final String message;
    
    public StatusMessage(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }
    
}
