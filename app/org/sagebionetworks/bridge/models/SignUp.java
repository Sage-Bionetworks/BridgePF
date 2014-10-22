package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class SignUp {

    private static final String EMAIL_FIELD = "email";
    private static final String USERNAME_FIELD = "username";
    private static final String PASSWORD_FIELD = "password";
    private static final String ROLES_FIELD = "roles";
    
    private final String username;
    private final String email;
    private final String password;
    private final List<String> roles;
    
    public SignUp(String username, String email, String password, String... roles) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.roles = (roles == null) ? null : Lists.newArrayList(roles);
    }
    
    public static final SignUp fromJson(JsonNode node, boolean allowRoles) {
        String username = JsonUtils.asText(node, USERNAME_FIELD);
        String email = JsonUtils.asText(node, EMAIL_FIELD);
        String password = JsonUtils.asText(node, PASSWORD_FIELD);
        String[] roles = (allowRoles) ? JsonUtils.asStringList(node, ROLES_FIELD).toArray(new String[] {}) : null;
        return new SignUp(username, email, password, roles);
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }
    
    public String getPassword() {
        return password;
    }

    public List<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "SignUp [username=" + username + ", email=" + email + ", password=" + password + ", roles=" + roles
                + "]";
    }
    
}
