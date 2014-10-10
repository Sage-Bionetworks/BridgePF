package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@DynamoDBTable(tableName = "SurveyQuestion")
@BridgeTypeName("SurveyQuestion")
public class DynamoSurveyQuestion implements SurveyQuestion, DynamoTable {
    
    private static final String CONSTRAINTS_PROPERTY = "constraints";
    private static final String DATA_TYPE_PROPERTY = "dataType";
    private static final String ENUM_PROPERTY = "enumeration";
    private static final String UI_HINTS_PROPERTY = "uiHint";
    private static final String PROMPT_PROPERTY = "prompt";
    
    private static final String IDENTIFIER_FIELD = "identifier";
    private static final String GUID_FIELD = "guid";
    
    public static SurveyQuestion fromJson(JsonNode node) {
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier( JsonUtils.asText(node, IDENTIFIER_FIELD) );
        question.setGuid( JsonUtils.asText(node, GUID_FIELD) );
        question.setPrompt(JsonUtils.asText(node, PROMPT_PROPERTY));
        question.setUiHint(JsonUtils.asUIHint(node, UI_HINTS_PROPERTY));
        question.setConstraints(JsonUtils.asConstraints(node, CONSTRAINTS_PROPERTY, DATA_TYPE_PROPERTY, ENUM_PROPERTY));
        return question;
    }

    private String surveyCompoundKey;
    private String guid;
    private String identifier;
    private int order;
    private String prompt; 
    private UIHint hint;
    private Constraints constraints;

    public DynamoSurveyQuestion() {
    }
    
    public DynamoSurveyQuestion(DynamoSurveyQuestion question) {
        setSurveyCompoundKey(question.getSurveyCompoundKey());
        setGuid(question.getGuid());
        setIdentifier(question.getIdentifier());
        setOrder(question.getOrder());
        setPrompt(question.getPrompt());
        setUiHint(question.getUiHint());
        setConstraints(question.getConstraints());
    }

    @Override
    @DynamoDBHashKey
    @JsonIgnore
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
    @JsonIgnore
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
    @DynamoDBIgnore
    public String getPrompt() {
        return prompt;
    }
    
    @Override
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    @Override
    @DynamoDBIgnore
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    public UIHint getUiHint() {
        return hint;
    }
    
    @Override
    public void setUiHint(UIHint hint) {
        this.hint = hint;
    }
    
    @Override
    @DynamoDBIgnore
    public Constraints getConstraints() {
        return constraints;
    }
    
    @Override
    public void setConstraints(Constraints constraints) {
        this.constraints = constraints;
    }

    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    @JsonIgnore
    public ObjectNode getData() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put(PROMPT_PROPERTY, prompt);
        data.put(UI_HINTS_PROPERTY, hint.name().toLowerCase());    
        data.put(CONSTRAINTS_PROPERTY, BridgeObjectMapper.get().valueToTree(constraints));    
        return data;
    }

    public void setData(ObjectNode data) {
        this.prompt = JsonUtils.asText(data, PROMPT_PROPERTY);
        this.hint = JsonUtils.asUIHint(data, UI_HINTS_PROPERTY);
        this.constraints = JsonUtils.asConstraints(data, CONSTRAINTS_PROPERTY, DATA_TYPE_PROPERTY, ENUM_PROPERTY);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((constraints == null) ? 0 : constraints.hashCode());
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + ((hint == null) ? 0 : hint.hashCode());
        result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
        result = prime * result + order;
        result = prime * result + ((prompt == null) ? 0 : prompt.hashCode());
        result = prime * result + ((surveyCompoundKey == null) ? 0 : surveyCompoundKey.hashCode());
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
        DynamoSurveyQuestion other = (DynamoSurveyQuestion) obj;
        if (constraints == null) {
            if (other.constraints != null)
                return false;
        } else if (!constraints.equals(other.constraints))
            return false;
        if (guid == null) {
            if (other.guid != null)
                return false;
        } else if (!guid.equals(other.guid))
            return false;
        if (hint != other.hint)
            return false;
        if (identifier == null) {
            if (other.identifier != null)
                return false;
        } else if (!identifier.equals(other.identifier))
            return false;
        if (order != other.order)
            return false;
        if (prompt == null) {
            if (other.prompt != null)
                return false;
        } else if (!prompt.equals(other.prompt))
            return false;
        if (surveyCompoundKey == null) {
            if (other.surveyCompoundKey != null)
                return false;
        } else if (!surveyCompoundKey.equals(other.surveyCompoundKey))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DynamoSurveyQuestion [surveyCompoundKey=" + surveyCompoundKey + ", guid=" + guid + ", identifier="
                + identifier + ", order=" + order + ", prompt=" + prompt + ", hint=" + hint + ", constraints="
                + constraints + "]";
    }

}
