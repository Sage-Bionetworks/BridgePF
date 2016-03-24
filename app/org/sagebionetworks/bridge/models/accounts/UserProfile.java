package org.sagebionetworks.bridge.models.accounts;


import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class UserProfile {
    
    private static final String FIRST_NAME_FIELD = "firstName";
    private static final String LAST_NAME_FIELD = "lastName";
    private static final String EMAIL_FIELD = "email";
    private static final String ROLES_FIELD = "roles";
    private static final String STATUS_FIELD = "status";
    private static final String HEALTH_CODE_FIELD = "healthCode";
    
    // These fields are potentially present in some exports of user profile data, 
    // coming from the StudyParticipant or AccountSummary classes. Custom attributes 
    // cannot be any of these field names so they won't conflict or be confusing.
    public static final Set<String> RESERVED_ATTR_NAMES = Sets.newHashSet(
            FIRST_NAME_FIELD,LAST_NAME_FIELD,EMAIL_FIELD,ROLES_FIELD,STATUS_FIELD,HEALTH_CODE_FIELD);
    static {
        for (ParticipantOption option : ParticipantOption.values()) {
            RESERVED_ATTR_NAMES.add(option.getFieldName());
        }
    }
    
    private String firstName;
    private String lastName;
    private String email;
    private Map<String,String> attributes;

    public UserProfile() {
        attributes = new HashMap<>();
    }

    public static UserProfile fromJson(Set<String> attributes, JsonNode node) {
        UserProfile profile = new UserProfile();
        profile.setFirstName(JsonUtils.asText(node, FIRST_NAME_FIELD));
        profile.setLastName(JsonUtils.asText(node, LAST_NAME_FIELD));
        for (String attribute : attributes) {
            String value = JsonUtils.asText(node, attribute);
            if (value != null) {
                profile.getAttributes().put(attribute, value);
            }
        }
        return profile;
    }

    /**
     * Provided for API compatibility, this is always the email address of the account. 
     * @deprecated
     */
    public String getUsername() {
        return this.email;
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
    public String getEmail() {
        return this.email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public void removeAttribute(String name) {
        if (isNotBlank(name)) {
            attributes.remove(name);
        }
    }
    public void setAttribute(String name, String value) {
        if (isNotBlank(name) && isNotBlank(value) && !UserProfile.RESERVED_ATTR_NAMES.contains(name)) {
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
