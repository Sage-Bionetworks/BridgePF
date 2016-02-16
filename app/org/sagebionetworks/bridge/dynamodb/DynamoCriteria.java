package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.Criteria;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;

@DynamoDBTable(tableName = "Criteria")
@BridgeTypeName("Criteria")
public final class DynamoCriteria implements Criteria {
    
    public String key;
    public Integer minAppVersion;
    public Integer maxAppVersion;
    public Set<String> allOfGroups = Sets.newHashSet();
    public Set<String> noneOfGroups = Sets.newHashSet();
    
    @Override
    @DynamoDBHashKey
    @JsonIgnore
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
        this.allOfGroups = (allOfGroups == null) ? Sets.newHashSet() : allOfGroups;
    }
    @Override
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    public Set<String> getNoneOfGroups() {
        return noneOfGroups;
    }
    public void setNoneOfGroups(Set<String> noneOfGroups) {
        this.noneOfGroups = (noneOfGroups == null) ? Sets.newHashSet() : noneOfGroups;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(key, minAppVersion, maxAppVersion, allOfGroups, noneOfGroups);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoCriteria other = (DynamoCriteria) obj;
        return Objects.equals(key,  other.key) && 
                Objects.equals(noneOfGroups, other.noneOfGroups) && 
                Objects.equals(allOfGroups, other.allOfGroups) && 
                Objects.equals(minAppVersion, other.minAppVersion) && 
                Objects.equals(maxAppVersion, other.maxAppVersion);
    }
    @Override
    public String toString() {
        return "DynamoCriteria [key=" + key + ", allOfGroups=" + allOfGroups + ", noneOfGroups=" + noneOfGroups
                + ", minAppVersion=" + minAppVersion + ", maxAppVersion=" + maxAppVersion + "]";
    }
    
}
