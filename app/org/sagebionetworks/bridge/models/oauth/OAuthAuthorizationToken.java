package org.sagebionetworks.bridge.models.oauth;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The payload of the authorization token that is sent by the client to retrieve an OAuth 2.0 access token.
 */
public final class OAuthAuthorizationToken {
    private final String vendorId;
    private final String authToken;
    
    @JsonCreator
    public OAuthAuthorizationToken(@JsonProperty("vendorId") String vendorId,
            @JsonProperty("authToken") String authToken) {
        this.vendorId = vendorId;
        this.authToken = authToken;
    }
    
    public String getVendorId() {
        return vendorId;
    }

    public String getAuthToken() {
        return authToken;
    }

    @Override
    public int hashCode() {
        return Objects.hash(authToken, vendorId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        OAuthAuthorizationToken other = (OAuthAuthorizationToken) obj;
        return Objects.equals(authToken, other.authToken)
                && Objects.equals(vendorId, other.vendorId);
    }

    @Override
    public String toString() {
        return "OAuthAuthorizationToken [vendorId=" + vendorId + ", authToken=" + authToken + "]";
    }
}
