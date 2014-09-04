package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.databind.JsonNode;

@DynamoDBTable(tableName = "SurveyAnswer")
public class DynamoSurveyAnswer implements SurveyAnswer, DynamoTable {

    private String surveyResponseGuid;
    private String surveyQuestionGuid;
    private JsonNode data;
    private Long version;
    
    @Override
    @DynamoDBHashKey
    public String getSurveyResponseGuid() {
        return surveyResponseGuid;
    }

    @Override
    public void setSurveyResponseGuid(String surveyResponseGuid) {
        this.surveyResponseGuid = surveyResponseGuid;
    }

    @Override
    @DynamoDBRangeKey
    public String getSurveyQuestionGuid() {
        return surveyQuestionGuid;
    }

    @Override
    public void setSurveyQuestionGuid(String surveyQuestionGuid) {
        this.surveyQuestionGuid = surveyQuestionGuid;
    }
    
    @Override
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override 
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    public JsonNode getData() {
        return data;
    }

    @Override
    public void setData(JsonNode data) {
        this.data = data;
    }

}
