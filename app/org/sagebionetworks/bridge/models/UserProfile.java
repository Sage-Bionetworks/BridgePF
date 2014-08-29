package org.sagebionetworks.bridge.models;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

import com.fasterxml.jackson.databind.JsonNode;

public class UserProfile {
    
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    
    private static final String FIRSTNAME = "firstName";
    private static final String LASTNAME = "lastName";

    public UserProfile(User user) {
        this.firstName = removeEmpty(user.getFirstName());
        this.lastName = removeEmpty(user.getLastName());
        this.username = removeEmpty(user.getUsername());
        this.email = removeEmpty(user.getEmail());
    }
    
    public UserProfile() {
    }
    
    public static UserProfile fromJson(JsonNode node) {
        if (node == null) {
            throw new BridgeServiceException("User JSON is null", 500);
        }
        UserProfile user = new UserProfile();
        if (node.get(FIRSTNAME) != null) {
            user.setFirstName(replaceWithEmpty(node.get(FIRSTNAME).asText()));
        }
        if (node.get(LASTNAME) != null) {
            user.setLastName(replaceWithEmpty(node.get(LASTNAME).asText()));
        }
        return user;
    }
    
    public String getFirstName() {
        return this.firstName;
    }
    public String getFirstNameWithEmptyString() {
        return replaceWithEmpty(this.firstName);
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return this.lastName;
    }
    public String getLastNameWithEmptyString() {
        return replaceWithEmpty(this.lastName);
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
    
    private static String replaceWithEmpty(String s) {
        if (StringUtils.isBlank(s)) {
            return "<EMPTY>";
        } else {
            return s;
        }
    }
    
    private String removeEmpty(String s) {
        if (StringUtils.isBlank(s) || s.equalsIgnoreCase("<EMPTY>")) {
            return "";
        } else {
            return s;
        }
    }

}
