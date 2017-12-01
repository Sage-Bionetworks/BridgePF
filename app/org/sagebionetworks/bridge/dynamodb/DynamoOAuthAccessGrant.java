package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "OAuthAccessGrant")
public class DynamoOAuthAccessGrant implements OAuthAccessGrant {
    private String key;
    private String healthCode;
    private String vendorId;
    private String accessToken;
    private String refreshToken;
    private long createdOn;
    private long expiresOn;
    private String providerUserId;
    
    @DynamoDBHashKey
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @DynamoDBRangeKey
    @Override
    public String getHealthCode() {
        return healthCode;
    }

    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    
    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public String getVendorId() {
        return vendorId;
    }

    @Override
    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    @Override
    public String getRefreshToken() {
        return refreshToken;
    }

    @Override
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Override
    public long getCreatedOn() {
        return createdOn;
    }

    @Override
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    public long getExpiresOn() {
        return expiresOn;
    }

    @Override
    public void setExpiresOn(long expiresOn) {
        this.expiresOn = expiresOn;
    }

    @Override
    public String getProviderUserId() {
        return providerUserId;
    }

    @Override
    public void setProviderUserId(String providerUserId) {
        this.providerUserId = providerUserId;
    }
}
