package org.sagebionetworks.bridge.models;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

import com.fasterxml.jackson.databind.JsonNode;

public class UserProfile implements Iterable<String> {
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String stormpathHref;
    
    private static final String FIRSTNAME = "firstName";
    private static final String LASTNAME = "lastName";

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
    
    // Get first name/last name from JsonNode, and
    // username, stormpathHref, and email from sessionUser.
    public static UserProfile fromJson(JsonNode node, UserProfile currentUser) {
        if (node == null)
            throw new BridgeServiceException("User JSON is null", 500);
 
        UserProfile user = new UserProfile();
        if (node.get(FIRSTNAME) != null) {
            user.setFirstName(replaceEmpty(node.get(FIRSTNAME).asText()));
        }
        if (node.get(LASTNAME) != null) {
            user.setLastName(replaceEmpty(node.get(LASTNAME).asText()));
        }

        user.setUsername(replaceEmpty(currentUser.getUsername()));
        user.setStormpathHref(replaceEmpty(currentUser.getStormpathHref()));
        user.setEmail(replaceEmpty(currentUser.getEmail()));

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

    @Override
    public Iterator<String> iterator() {
        String[] fieldsArr = {this.firstName, this.lastName, this.email, this.username, this.stormpathHref};
        List<String> fields = Arrays.asList(fieldsArr);
        return fields.iterator();
    }
    
    private static String replaceEmpty(String s) {
        if (s.equalsIgnoreCase("")) {
            return "<EMPTY>";
        }
        else {
            return s;
        }
    }

}
