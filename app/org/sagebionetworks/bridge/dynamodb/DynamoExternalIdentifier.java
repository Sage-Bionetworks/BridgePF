package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

@DynamoDBTable(tableName = "ExternalIdentifier")
public class DynamoExternalIdentifier implements ExternalIdentifier {

    private String externalId;
    private String filterableExternalId;
    private String studyId;
    private String healthCode;
    private long reservation;
    
    public DynamoExternalIdentifier() {}
    
    public DynamoExternalIdentifier(StudyIdentifier studyId, String externalId) {
        this.studyId = studyId.getIdentifier();
        this.externalId = externalId;
        this.filterableExternalId = externalId;
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
    public String getExternalId() {
        return externalId;
    }
    @Override
    @JsonSetter("externalId")
    public void setExternalId(String externalId) {
        this.externalId = externalId;
        this.filterableExternalId = externalId;
    }
    // For backwards compatibility with the "set external ID" API
    @JsonSetter("identifier")
    private void setIdentifier(String identifier) {
        this.externalId = identifier;
        this.filterableExternalId = identifier;
    }
    /**
     * DynamoDB will not filter a query on the value of the hash key, so we copy this 
     * value to another column where we can filter on it. This is not exposed outside
     * of the DAO.
     */
    @DynamoDBAttribute
    @JsonIgnore
    public String getFilterableExternalId() {
        return filterableExternalId;
    }
    public void setFilterableExternalId(String filterableExternalId) {
        this.filterableExternalId = filterableExternalId;
    }
    @DynamoDBAttribute
    @JsonIgnore
    @Override
    public String getHealthCode() {
        return healthCode;
    }
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @DynamoDBAttribute
    @JsonIgnore
    @Override
    public long getReservation() {
        return reservation;
    }
    @Override
    public void setReservation(long reservation) {
        this.reservation = reservation;
    }

    @Override
    public String toString() {
        return "DynamoExternalIdentifier [studyId=" + studyId + ", externalId=" + externalId + ", healthCode="
                + (healthCode == null ? "null" : "[REDACTED]") + ", reservation=" + reservation + "]";
    }
    
}
