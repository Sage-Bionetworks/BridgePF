package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementConstants;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

@DynamoDBTable(tableName = SurveyElementConstants.SURVEY_ELEMENT_TYPE)
@JsonFilter("filter")
public class DynamoSurveyElement implements SurveyElement {

    private String surveyCompoundKey;
    private String guid;
    private String identifier;
    private String type;
    private int order;
    private JsonNode data;
    private List<SurveyRule> beforeRules;
    private List<SurveyRule> afterRules;

    public DynamoSurveyElement() {
    }

    @DynamoDBHashKey
    public String getSurveyCompoundKey() {
        return surveyCompoundKey;
    }
    public void setSurveyCompoundKey(String surveyCompoundKey) {
        this.surveyCompoundKey = surveyCompoundKey;
    }
    public void setSurveyKeyComponents(String surveyGuid, long createdOn) {
        this.surveyCompoundKey = surveyGuid + ":" + Long.toString(createdOn);
    }
    @DynamoDBAttribute
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    @DynamoDBRangeKey
    @JsonIgnore
    public int getOrder() {
        return order;
    }
    public void setOrder(int order) {
        this.order = order;
    }
    @DynamoDBAttribute
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    @DynamoDBAttribute
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    /**
     * The JSON data node is set on this class, which maps 1:1 with the Dynamo table. 
     * In the SurveyElementFactory, this object is used to initialize a sub-class where
     * the data is then parsed into the fields specific to that subclass. This is because
     * DynamoDB's SDK for Java is not a complete ORM solution that supports mapping multiple 
     * inheritance.
     */
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    public JsonNode getData() {
        return data;
    }
    public void setData(JsonNode data) {
        this.data = data;
    }
    /**
     * For backwards compatibility purposes, a null property here is different than an 
     * empty list. A null value indicates we have never moved the rules from the constraints 
     * and persisted them as a property of the element; an empty list is a valid value. 
     */
    @DynamoDBTypeConverted(converter = SurveyRuleListMarshaller.class)
    @DynamoDBAttribute
    public List<SurveyRule> getAfterRules() {
        return (this.afterRules == null) ? null : ImmutableList.copyOf(this.afterRules);
    }
    public void setAfterRules(List<SurveyRule> afterRules) {
        this.afterRules = afterRules;
    }
    @DynamoDBTypeConverted(converter = SurveyRuleListMarshaller.class)
    @DynamoDBAttribute
    public List<SurveyRule> getBeforeRules() {
        return (this.beforeRules == null) ? null : ImmutableList.copyOf(this.beforeRules);
    }
    public void setBeforeRules(List<SurveyRule> beforeRules) {
        this.beforeRules = beforeRules;
    }
}
