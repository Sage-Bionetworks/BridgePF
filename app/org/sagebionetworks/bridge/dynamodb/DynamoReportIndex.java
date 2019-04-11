package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.reports.ReportIndex;

import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonIgnore;

@DynamoDBTable(tableName = "ReportIndex")
public class DynamoReportIndex implements ReportIndex {

    private String key;
    private String identifier;
    private Set<String> substudyIds;
    private boolean isPublic;
    
    @JsonIgnore
    @DynamoDBHashKey
    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }
    
    @DynamoDBRangeKey
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;

    }
    
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    @Override
    public Set<String> getSubstudyIds(){
        return this.substudyIds;
    }
    
    @Override
    public void setSubstudyIds(Set<String> substudyIds) {
        this.substudyIds = substudyIds;
    }
    
    @DynamoDBAttribute
    @Override
    public boolean isPublic() {
        return isPublic;
    }
    
    @Override
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }
}
