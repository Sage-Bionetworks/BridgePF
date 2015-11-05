package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "FPHSExternalIdentifier")
public class DynamoFPHSExternalIdentifier implements FPHSExternalIdentifier {

    private String externalIdentifier;
    private boolean registered;
    
    @DynamoDBHashKey
    @Override
    public String getExternalId() {
        return externalIdentifier;
    }

    @Override
    public void setExternalId(String externalIdentifier) {
        this.externalIdentifier = externalIdentifier;
    }

    @DynamoDBAttribute
    @Override
    public boolean getRegistered() {
        return registered;
    }

    @Override
    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

}
