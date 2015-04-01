package org.sagebionetworks.bridge.models;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class UserProfile {
    
    public static final String FIRST_NAME_FIELD = "firstName";
    public static final String LAST_NAME_FIELD = "lastName";
    public static final String EMAIL_FIELD = "email";
    public static final String USERNAME_FIELD = "username";
    public static final String PHONE_FIELD = "phone";
    /**
     * These fields are not part of the profile, but they are used on export to expose the participant option values, so
     * studies cannot override these values as extended user profile attributes.
     */
    public static final String SHARING_SCOPE_FIELD = "sharing";
    public static final String NOTIFY_BY_EMAIL_FIELD = "notifyByEmail";
    
    public static final Set<String> FIXED_PROPERTIES = Sets.newHashSet(FIRST_NAME_FIELD, LAST_NAME_FIELD,
            PHONE_FIELD, EMAIL_FIELD, USERNAME_FIELD, SHARING_SCOPE_FIELD);
    
    private String firstName;
    private String lastName;
    private String username;
    private String phone;
    private String email;
    private Map<String,String> attributes;

    public UserProfile() {
        attributes = new HashMap<>();
    }

    public static UserProfile fromJson(Set<String> attributes, JsonNode node) {
        UserProfile profile = new UserProfile();
        profile.setFirstName(JsonUtils.asText(node, FIRST_NAME_FIELD));
        profile.setLastName(JsonUtils.asText(node, LAST_NAME_FIELD));
        profile.setPhone(JsonUtils.asText(node, PHONE_FIELD));
        for (String attribute : attributes) {
            String value = JsonUtils.asText(node, attribute);
            if (value != null) {
                profile.getAttributes().put(attribute, value);
            }
        }
        return profile;
    }
    
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
    
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    public void removeAttribute(String name) {
        if (isNotBlank(name)) {
            attributes.remove(name);
        }
    }
    public void setAttribute(String name, String value) {
        if (isNotBlank(name) && isNotBlank(value) && !UserProfile.FIXED_PROPERTIES.contains(name)) {
            attributes.put(name, value);
        }
    }
    public String getAttribute(String name) {
        return attributes.get(name);
    }
    // These next two cause Jackson to add attributes to the properties of the 
    // UserProfile object (flattened... not as the value of an attributes property).
    @JsonAnyGetter
    Map<String,String> getAttributes() {
        return attributes;
    }
    @JsonAnySetter
    void setAttributes(Map<String,String> attributes) {
        this.attributes = attributes;
    }

}
