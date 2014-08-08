package org.sagebionetworks.bridge.models;

public class UserProfileInfo {
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    
    public UserProfileInfo(UserProfile user) {
        this.firstName = removeEmpty(user.getFirstName());
        this.lastName = removeEmpty(user.getLastName());
        this.username = removeEmpty(user.getUsername());
        this.email = removeEmpty(user.getEmail());
    }
    
    public String getFirstName() {
        return this.firstName;
    }
    public String getLastName() {
        return this.lastName;
    }
    public String getUsername() {
        return this.username;
    }
    public String getEmail() {
        return this.email;
    }
    private String removeEmpty(String s) {
        if (s.equalsIgnoreCase("<EMPTY>")) {
            return "";
        }
        else {
            return s;
        }
    }
}
