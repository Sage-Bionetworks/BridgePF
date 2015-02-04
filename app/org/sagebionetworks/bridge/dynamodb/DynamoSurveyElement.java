package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.surveys.SurveyElement;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.databind.JsonNode;

@DynamoDBTable(tableName = SurveyElement.SURVEY_ELEMENT_TYPE)
public class DynamoSurveyElement implements SurveyElement, DynamoTable {

    protected String surveyCompoundKey;
    protected String guid;
    protected String identifier;
    protected String type;
    protected int order;
    private JsonNode data;

    public DynamoSurveyElement() {
    }

    /*
    public DynamoSurveyElement(SurveyElement element, boolean boo) {
        setSurveyCompoundKey(element.getSurveyCompoundKey());
        setGuid(element.getGuid());
        setIdentifier(element.getIdentifier());
        setType(element.getType());
        setData(element.getData());
        setOrder(element.getOrder());
    }
*/
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
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    public JsonNode getData() {
        return data;
    }
    public void setData(JsonNode data) {
        this.data = data;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
        result = prime * result + order;
        result = prime * result + ((surveyCompoundKey == null) ? 0 : surveyCompoundKey.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DynamoSurveyElement other = (DynamoSurveyElement) obj;
        if (guid == null) {
            if (other.guid != null)
                return false;
        } else if (!guid.equals(other.guid))
            return false;
        if (identifier == null) {
            if (other.identifier != null)
                return false;
        } else if (!identifier.equals(other.identifier))
            return false;
        if (order != other.order)
            return false;
        if (surveyCompoundKey == null) {
            if (other.surveyCompoundKey != null)
                return false;
        } else if (!surveyCompoundKey.equals(other.surveyCompoundKey))
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        return true;
    }
    
}
