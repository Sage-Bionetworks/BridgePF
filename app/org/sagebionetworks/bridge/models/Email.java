package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.JsonNode;

public class Email {

    private static final String EMAIL_FIELD = "email";
    
    private final String email;
    
    public Email(String email) {
        this.email = email;
    }
    
    public static final Email fromJson(JsonNode node) {
        String email = null;
        if (node != null && node.get(EMAIL_FIELD) != null) {
            email = node.get(EMAIL_FIELD).asText();
        }
        return new Email(email);
    }

    public String getEmail() {
        return email;
    }

}
