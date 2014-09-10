package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

public class Email {

    private static final String EMAIL_FIELD = "email";
    
    private final String email;
    
    public Email(String email) {
        this.email = email;
    }
    
    public static final Email fromJson(JsonNode node) {
        String email = JsonUtils.asText(node, EMAIL_FIELD);
        return new Email(email);
    }

    public String getEmail() {
        return email;
    }

}
