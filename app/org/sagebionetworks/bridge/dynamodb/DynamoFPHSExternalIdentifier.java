package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;

@DynamoDBTable(tableName = "FPHSExternalIdentifier")
public class DynamoFPHSExternalIdentifier implements FPHSExternalIdentifier {

    private String externalId;
    private boolean registered;
    
    public DynamoFPHSExternalIdentifier() {
        
    }
    
    public DynamoFPHSExternalIdentifier(String externalId) {
        this.externalId = externalId;
    }
    
    @Override
    @DynamoDBHashKey
    public String getExternalId() {
        return externalId;
    }

    @Override
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    @JsonIgnore
    @Override
    @DynamoDBAttribute
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

}
