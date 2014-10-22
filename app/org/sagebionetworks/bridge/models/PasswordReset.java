package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

public class PasswordReset implements BridgeEntity {

    private static final String PASSWORD_FIELD = "password";
    private static final String SPTOKEN_FIELD = "sptoken";
    
    private final String password;
    private final String sptoken;
    
    public PasswordReset(String password, String sptoken) {
        this.password = password;
        this.sptoken = sptoken;
    }
    
    public static final PasswordReset fromJson(JsonNode node) {
        String password = JsonUtils.asText(node, PASSWORD_FIELD);
        String sptoken = JsonUtils.asText(node, SPTOKEN_FIELD);
        return new PasswordReset(password, sptoken);
    }

    public String getPassword() {
        return password;
    }

    public String getSptoken() {
        return sptoken;
    }

}
