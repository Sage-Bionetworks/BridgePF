package org.sagebionetworks.bridge.models.accounts;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SignUp implements BridgeEntity {

    private final String email;
    private final String password;
    private final Set<Roles> roles;
    private final Set<String> dataGroups;

    @JsonCreator
    public SignUp(@JsonProperty("email") String email, @JsonProperty("password") String password,
            @JsonProperty("roles") Set<Roles> roles, @JsonProperty("dataGroups") Set<String> dataGroups) {
        this.email = email;
        this.password = password;
        this.roles = BridgeUtils.nullSafeImmutableSet(roles);
        this.dataGroups = BridgeUtils.nullSafeImmutableSet(dataGroups);
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
        return Objects.hash(email, password, roles, dataGroups);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SignUp other = (SignUp) obj;
        return (Objects.equals(email, other.email) && Objects.equals(password, other.password) && 
                Objects.equals(roles, other.roles) && Objects.equals(dataGroups, other.dataGroups));
    }

    @Override
    public String toString() {
        return "SignUp [email=" + email + ", password=" + password + ", roles=" + roles + ", dataGroups="+ dataGroups +"]";
    }

}
