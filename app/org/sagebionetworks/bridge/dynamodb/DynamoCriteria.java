package org.sagebionetworks.bridge.dynamodb;

import java.util.Set;

import org.sagebionetworks.bridge.models.Criteria;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "Criteria")
public class DynamoCriteria implements Criteria {
    
    public String key;
    public Integer minAppVersion;
    public Integer maxAppVersion;
    public Set<String> allOfGroups;
    public Set<String> noneOfGroups;
    
    @Override
    @DynamoDBHashKey
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    @Override
    @DynamoDBAttribute    
    public Integer getMinAppVersion() {
        return minAppVersion;
    }
    public void setMinAppVersion(Integer minAppVersion) {
        this.minAppVersion = minAppVersion;
    }
    @Override
    @DynamoDBAttribute
    public Integer getMaxAppVersion() {
        return maxAppVersion;
    }
    public void setMaxAppVersion(Integer maxAppVersion) {
        this.maxAppVersion = maxAppVersion;
    }
    @Override
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    public Set<String> getAllOfGroups() {
        return allOfGroups;
    }
    public void setAllOfGroups(Set<String> allOfGroups) {
        this.allOfGroups = allOfGroups;
    }
    @Override
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    public Set<String> getNoneOfGroups() {
        return noneOfGroups;
    }
    public void setNoneOfGroups(Set<String> noneOfGroups) {
        this.noneOfGroups = noneOfGroups;
    }
    
}
