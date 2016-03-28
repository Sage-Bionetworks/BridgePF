package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

/**
 * Implementation of external identifier. This is used to deserialize JSON but is never 
 * returned as such through the API.
 */
@DynamoDBTable(tableName = "ExternalIdentifier")
public class DynamoExternalIdentifier implements ExternalIdentifier {

    private String identifier;
    private String filterableIdentifier;
    private String studyId;
    private String healthCode;
    private long reservation;
    
    public DynamoExternalIdentifier() {}
    
    public DynamoExternalIdentifier(StudyIdentifier studyId, String identifier) {
        this.studyId = studyId.getIdentifier();
        this.identifier = identifier;
        this.filterableIdentifier = identifier;
    }
    
    @DynamoDBHashKey
    @Override
    public String getStudyId() {
        return studyId;
    }
    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    @DynamoDBRangeKey
    @Override
    public String getIdentifier() {
        return identifier;
    }
    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
        this.filterableIdentifier = identifier;
    }
    /**
     * DynamoDB will not filter a query on the value of the hash key, so we copy this 
     * value to another column where we can filter on it. This is not exposed outside
     * of the DAO.
     */
    @DynamoDBAttribute
    public String getFilterableIdentifier() {
        return filterableIdentifier;
    }
    public void setFilterableIdentifier(String filterableIdentifier) {
        this.filterableIdentifier = filterableIdentifier;
    }
    @DynamoDBAttribute
    @Override
    public String getHealthCode() {
        return healthCode;
    }
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @DynamoDBAttribute
    @Override
    public long getReservation() {
        return reservation;
    }
    @Override
    public void setReservation(long reservation) {
        this.reservation = reservation;
    }
    
}
