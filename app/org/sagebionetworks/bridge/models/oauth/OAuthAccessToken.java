package org.sagebionetworks.bridge.models.oauth;

import java.util.Objects;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class OAuthAccessToken {
    private final String vendorId;
    private final String accessToken;
    private final DateTime expiresOn;
    
    @JsonCreator
    public OAuthAccessToken(@JsonProperty("vendorId") String vendorId, @JsonProperty("accessToken") String accessToken,
            @JsonProperty("expiresOn") DateTime expiresOn) {
        this.vendorId = vendorId;
        this.accessToken = accessToken;
        this.expiresOn = expiresOn;
    }
    
    public String getVendorId() {
        return vendorId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public DateTime getExpiresOn() {
        return expiresOn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(vendorId, accessToken, expiresOn);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        OAuthAccessToken other = (OAuthAccessToken) obj;
        return Objects.equals(vendorId, other.vendorId)
                && Objects.equals(accessToken, other.accessToken)
                && Objects.equals(expiresOn, other.expiresOn);
    }

    @Override
    public String toString() {
        return "OAuthAccessToken [vendorId=" + vendorId + ", accessToken=" + accessToken + 
                ", expiresOn=" + expiresOn + "]";
    }
}
