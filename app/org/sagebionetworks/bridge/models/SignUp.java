package org.sagebionetworks.bridge.models;

import java.util.Set;

import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class SignUp implements BridgeEntity {

    private static final String EMAIL_FIELD = "email";
    private static final String USERNAME_FIELD = "username";
    private static final String PASSWORD_FIELD = "password";
    private static final String ROLES_FIELD = "roles";

    private final String username;
    private final String email;
    private final String password;
    private final Set<String> roles;

    public SignUp(String username, String email, String password, Set<String> roles) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.roles = (roles == null) ? null : Sets.newHashSet(roles);
    }

    public static final SignUp fromJson(JsonNode node, boolean allowRoles) {
        String username = JsonUtils.asText(node, USERNAME_FIELD);
        String email = JsonUtils.asText(node, EMAIL_FIELD);
        String password = JsonUtils.asText(node, PASSWORD_FIELD);
        Set<String> roles = (allowRoles) ? JsonUtils.asStringSet(node, ROLES_FIELD) : null;
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

    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "SignUp [username=" + username + ", email=" + email + ", password=" + password + ", roles=" + roles
                + "]";
    }

}
