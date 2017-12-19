package org.sagebionetworks.bridge.models.oauth;

import org.sagebionetworks.bridge.dynamodb.DynamoOAuthAccessGrant;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Record of an access grant from an OAuth 2.0 provider.
 *
 */
public interface OAuthAccessGrant extends BridgeEntity {

    public static OAuthAccessGrant create() {
        return new DynamoOAuthAccessGrant();
    }
    
    /**
     * The vendor ID for the OAuth provider issuing the grant. This value is defined by Bridge and is scoped 
     * to a study.
     */
    public String getVendorId();
    public void setVendorId(String vendorId);
    
    /**
     * Health code of the participant with the grant.
     */
    public String getHealthCode();
    public void setHealthCode(String healthCode);
        
    /**
     * The access token of an OAuth 2.0 grant.
     */
    public String getAccessToken();
    public void setAccessToken(String accessToken);
    
    /**
     * The refresh token of an OAuth 2.0 grant.
     */
    public String getRefreshToken();
    public void setRefreshToken(String refreshToken);
    
    /**
     * The timestamp (millis since epoch) when the Bridge access grant was created.
     */
    public long getCreatedOn();
    public void setCreatedOn(long createdOn);
    
    /**
     * The timestamp (millis since epoch) when the access grant should expire. This is calculated 
     * from the expiration period of the grant and the time when it was created. This is not 
     * guaranteed to be true, since a user (external to Bridge) can revoke the grant.
     */
    public long getExpiresOn();
    public void setExpiresOn(long expiresOn);
    
    /**
     * The identifier used by the service provider to identify the user.
     */
    public String getProviderUserId();
    public void setProviderUserId(String providerUserId);
    
}
