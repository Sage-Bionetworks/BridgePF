package org.sagebionetworks.bridge.models.studies;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class OAuthProvider {
    private final String clientId;
    private final String secret;
    private final String endpoint;
    
    @JsonCreator
    public OAuthProvider(@JsonProperty("clientId") String clientId, @JsonProperty("secret") String secret,
            @JsonProperty("endpoint") String endpoint) {
        this.clientId = clientId;
        this.secret = secret;
        this.endpoint = endpoint;
    }
    public String getClientId() {
        return clientId;
    }
    public String getSecret() {
        return secret;
    }
    public String getEndpoint() {
        return endpoint;
    }
    @Override
    public int hashCode() {
        return Objects.hash(clientId, endpoint, secret);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        OAuthProvider other = (OAuthProvider) obj;
        return Objects.equals(clientId, other.clientId) 
               && Objects.equals(endpoint, other.endpoint) 
               && Objects.equals(secret, other.secret);
    }
    @Override
    public String toString() {
        return "OAuthProvider [clientId=" + clientId + ", endpoint=" + endpoint + ", secret=REDACTED]";
    }
}
