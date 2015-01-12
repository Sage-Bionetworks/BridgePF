package org.sagebionetworks.bridge.models;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

public class UserProfile {
    
    private String firstName;
    private String lastName;
    private String username;
    private String phone;
    private String email;
    
    private static final String FIRST_NAME_FIELD = "firstName";
    private static final String LAST_NAME_FIELD = "lastName";
    private static final String PHONE_NAME_FIELD = "phone";
    
    public UserProfile() {
    }

    public static UserProfile fromJson(JsonNode node) {
        UserProfile profile = new UserProfile();
        String firstName = JsonUtils.asText(node, FIRST_NAME_FIELD);
        String lastName = JsonUtils.asText(node, LAST_NAME_FIELD);
        profile.setFirstName(replaceWithEmpty(firstName));
        profile.setLastName(replaceWithEmpty(lastName));
        profile.setPhone(JsonUtils.asText(node, PHONE_NAME_FIELD));
        return profile;
    }
    
    public String getFirstName() {
        return this.firstName;
    }
    @JsonIgnore
    public String getFirstNameWithEmptyString() {
        return replaceWithEmpty(this.firstName);
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return this.lastName;
    }
    @JsonIgnore
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
    
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    private static String replaceWithEmpty(String s) {
        if (StringUtils.isBlank(s)) {
            return "<EMPTY>";
        } else {
            return s;
        }
    }

}
