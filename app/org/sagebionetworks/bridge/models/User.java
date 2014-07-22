package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

import com.fasterxml.jackson.databind.JsonNode;

public class User {
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String stormpathHref;
    
    private static final String FIRSTNAME = "firstName";
    private static final String LASTNAME  = "lastName";
    private static final String EMAIL     = "email";
    
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

    public static User fromJson(JsonNode node, UserSession session) {
        if (node == null) throw new BridgeServiceException("User JSON is null", 500);
        
        User user = new User();
        if (node.get(FIRSTNAME) != null) user.setFirstName( node.get(FIRSTNAME).asText() );
        if (node.get(LASTNAME)  != null) user.setLastName(  node.get(LASTNAME) .asText() );
        if (node.get(EMAIL)     != null) user.setEmail(     node.get(EMAIL)    .asText() );
        
        user.setUsername(      session.getUsername() );
        user.setStormpathHref( session.getStormpathHref() );
        
        return user;
    }
    
    public static boolean isValidUser(User user) {
        if ( user.getEmail()         == null
          || user.getFirstName()     == null
          || user.getLastName()      == null 
          || user.getStormpathHref() == null
          || user.getUsername()      == null )
            return false;
        
        return true;
    }
}
