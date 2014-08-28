package org.sagebionetworks.bridge.models;

import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.group.Group;

public class User {

    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String healthDataCode;
    private String studyKey;
    private boolean consent;
    private Set<String> roles = Sets.newHashSet();

    public User() {
    }

    public User(Account account) {
        this.email = account.getEmail();
        this.username = account.getUsername();
        this.firstName = account.getGivenName();
        this.lastName = account.getSurname();
        this.id = account.getHref().split("/accounts/")[1];
        for (Iterator<Group> i = account.getGroups().iterator(); i.hasNext();) {
            roles.add(i.next().getName());
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getHealthDataCode() {
        return healthDataCode;
    }

    public void setHealthDataCode(String healthDataCode) {
        this.healthDataCode = healthDataCode;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStudyKey() {
        return studyKey;
    }

    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public boolean doesConsent() {
        return consent;
    }

    // Jackson serialization needs this method, even though linguistically, it makes no sense.
    public boolean isConsent() {
        return consent;
    }

    public void setConsent(boolean consent) {
        this.consent = consent;
    }

    public boolean isInRole(String role) {
        return this.roles.contains(role);
    }

    public String getType() {
        return this.getClass().getSimpleName();
    }
    
    @JsonIgnore
    public String getStormpathHref() {
        return (id == null) ? null : ("https://api.stormpath.com/v1/accounts/" + id);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (consent ? 1231 : 1237);
        result = prime * result + ((email == null) ? 0 : email.hashCode());
        result = prime * result + ((firstName == null) ? 0 : firstName.hashCode());
        result = prime * result + ((healthDataCode == null) ? 0 : healthDataCode.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((lastName == null) ? 0 : lastName.hashCode());
        result = prime * result + ((roles == null) ? 0 : roles.hashCode());
        result = prime * result + ((studyKey == null) ? 0 : studyKey.hashCode());
        result = prime * result + ((username == null) ? 0 : username.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        User other = (User) obj;
        if (consent != other.consent)
            return false;
        if (email == null) {
            if (other.email != null)
                return false;
        } else if (!email.equals(other.email))
            return false;
        if (firstName == null) {
            if (other.firstName != null)
                return false;
        } else if (!firstName.equals(other.firstName))
            return false;
        if (healthDataCode == null) {
            if (other.healthDataCode != null)
                return false;
        } else if (!healthDataCode.equals(other.healthDataCode))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (lastName == null) {
            if (other.lastName != null)
                return false;
        } else if (!lastName.equals(other.lastName))
            return false;
        if (roles == null) {
            if (other.roles != null)
                return false;
        } else if (!roles.equals(other.roles))
            return false;
        if (studyKey == null) {
            if (other.studyKey != null)
                return false;
        } else if (!studyKey.equals(other.studyKey))
            return false;
        if (username == null) {
            if (other.username != null)
                return false;
        } else if (!username.equals(other.username))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "User [id=" + id + ", username=" + username + ", firstName=" + firstName + ", lastName=" + lastName
                + ", email=" + email + ", healthDataCode=" + healthDataCode + ", studyKey=" + studyKey + ", consent="
                + consent + ", roles=" + roles + "]";
    }
}
