package org.sagebionetworks.bridge.models;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

public final class SignIn {

    private static final String PASSWORD_FIELD = "password";
    private static final String USERNAME_FIELD = "username";
    
    private final String username;
    private final String password;
    
    public SignIn(String username, String password) {
        this.username = username;
        this.password = password;
    }
    
    public static final SignIn fromJson(JsonNode node) {
        String username = JsonUtils.asText(node, USERNAME_FIELD);
        String password = JsonUtils.asText(node, PASSWORD_FIELD);
        return new SignIn(username, password);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
    
    public boolean isBlank() {
        return StringUtils.isBlank(username) && StringUtils.isBlank(password);
    }
}
