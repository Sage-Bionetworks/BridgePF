package org.sagebionetworks.bridge.models.accounts;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;

public final class SignUp implements BridgeEntity {

    private final String username;
    private final String email;
    private final String password;
    private final Set<Roles> roles;
    private final Set<String> dataGroups;

    @JsonCreator
    public SignUp(@JsonProperty("username") String username, @JsonProperty("email") String email, 
            @JsonProperty("password") String password, @JsonProperty("roles") Set<Roles> roles, 
            @JsonProperty("dataGroups") Set<String> dataGroups) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.roles = (roles == null) ? Sets.newHashSet() : roles;
        this.dataGroups = (dataGroups == null) ? Sets.newHashSet() : dataGroups;
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

    public Set<Roles> getRoles() {
        return roles;
    }
    
    public Set<String> getDataGroups() {
        return dataGroups;
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, password, roles, username, dataGroups);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SignUp other = (SignUp) obj;
        return (Objects.equals(email, other.email) && Objects.equals(password, other.password) && 
                Objects.equals(roles, other.roles) && Objects.equals(username,  other.username) && 
                Objects.equals(dataGroups, other.dataGroups));
    }

    @Override
    public String toString() {
        return "SignUp [username=" + username + ", email=" + email + ", password=" + password + ", roles=" + roles + ", dataGroups="+ dataGroups +"]";
    }

}
