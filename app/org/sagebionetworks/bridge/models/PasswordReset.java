package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.JsonNode;

public class PasswordReset {

    private static final String PASSWORD_FIELD = "password";
    private static final String SPTOKEN_FIELD = "sptoken";
    
    private final String password;
    private final String sptoken;
    
    public PasswordReset(String password, String sptoken) {
        this.password = password;
        this.sptoken = sptoken;
    }
    
    public static final PasswordReset fromJson(JsonNode node) {
        String password = null;
        String sptoken = null;
        if (node != null && node.get(PASSWORD_FIELD) != null) {
            password = node.get(PASSWORD_FIELD).asText();
        }
        if (node != null && node.get(SPTOKEN_FIELD) != null) {
            sptoken = node.get(SPTOKEN_FIELD).asText();
        }
        return new PasswordReset(password, sptoken);
    }

    public String getPassword() {
        return password;
    }

    public String getSptoken() {
        return sptoken;
    }
    
    public String getType() {
        return this.getClass().getSimpleName();
    }

}
