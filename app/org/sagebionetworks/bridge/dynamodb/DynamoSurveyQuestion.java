package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.databind.JsonNode;

@DynamoDBTable(tableName = "SurveyQuestion")
public class DynamoSurveyQuestion implements SurveyQuestion, DynamoTable {

    private String surveyCompoundKey;
    private String guid;
    private String identifier;
    private int order;
    private JsonNode data;
    
    public DynamoSurveyQuestion() {
    }
    
    public DynamoSurveyQuestion(SurveyQuestion question) {
        setSurveyCompoundKey(question.getSurveyCompoundKey());
        setGuid(question.getGuid());
        setIdentifier(question.getIdentifier());
        setOrder(question.getOrder());
        setData(question.getData().deepCopy());
    }

    @Override
    @DynamoDBHashKey
    public String getSurveyCompoundKey() {
        return surveyCompoundKey;
    }

    @Override
    public void setSurveyCompoundKey(String surveyCompoundKey) {
        this.surveyCompoundKey = surveyCompoundKey;
    }

    @Override
    public void setSurveyKeyComponents(String surveyGuid, long versionedOn) {
        this.surveyCompoundKey = surveyGuid + ":" + Long.toString(versionedOn);
    }

    @Override
    @DynamoDBAttribute
    public String getGuid() {
        return guid;
    }

    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }

    @Override
    @DynamoDBRangeKey
    public int getOrder() {
        return order;
    }

    @Override
    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    @DynamoDBAttribute
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    public JsonNode getData() {
        return data;
    }

    @Override
    public void setData(JsonNode data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "DynamoSurveyQuestion [surveyCompoundKey=" + surveyCompoundKey + ", guid=" + guid + ", identifier="
                + identifier + ", order=" + order + ", data=" + data + "]";
    }

}
