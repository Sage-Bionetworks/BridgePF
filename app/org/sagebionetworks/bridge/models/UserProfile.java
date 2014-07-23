package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

import com.fasterxml.jackson.databind.JsonNode;

public class UserProfile {
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String stormpathHref;

    private static final String FIRSTNAME = "firstName";
    private static final String LASTNAME = "lastName";
    private static final String EMAIL = "email";

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return this.lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStormpathHref() {
        return this.stormpathHref;
    }

    public void setStormpathHref(String stormpathHref) {
        this.stormpathHref = stormpathHref;
    }

    // Get first name, last name, and email from JsonNode, and
    // username/stormpathHref from sessionUser.
    public static UserProfile fromJson(JsonNode node, UserProfile sessionUser) {
        if (node == null)
            throw new BridgeServiceException("User JSON is null", 500);

        UserProfile user = new UserProfile();
        if (node.get(FIRSTNAME) != null) {
            user.setFirstName(node.get(FIRSTNAME).asText());
        }
        if (node.get(LASTNAME) != null) {
            user.setLastName(node.get(LASTNAME).asText());
        }
        if (node.get(EMAIL) != null) {
            user.setEmail(node.get(EMAIL).asText());
        }

        user.setUsername(sessionUser.getUsername());
        user.setStormpathHref(sessionUser.getStormpathHref());

        return user;
    }
    
    public static boolean isValidUser(UserProfile user) {
        if (user.getEmail() == null 
                || user.getFirstName() == null 
                || user.getLastName() == null
                || user.getStormpathHref() == null 
                || user.getUsername() == null) {
            return false;
        } else {
            return true;
        }
    }

}
