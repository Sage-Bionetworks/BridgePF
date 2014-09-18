package org.sagebionetworks.bridge.dynamodb;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@DynamoDBTable(tableName = "SurveyQuestion")
public class DynamoSurveyQuestion implements SurveyQuestion, DynamoTable {
    
    private static final ObjectMapper mapper = new ObjectMapper();

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
        // question.getData() is not serialized, access the individual values. They are written 
        // back to the data node.
        question.setPrompt(JsonUtils.asText(node, PROMPT_PROPERTY));
        question.setUiHint(JsonUtils.asUIHint(node, UI_HINTS_PROPERTY));
        question.setConstraints(JsonUtils.asConstraints(node, CONSTRAINTS_PROPERTY, DATA_TYPE_PROPERTY, ENUM_PROPERTY));
        return question;
    }

    private String surveyCompoundKey;
    private String guid;
    private String identifier;
    private int order;
    private ObjectNode data;
    
    public DynamoSurveyQuestion() {
        this.data = JsonNodeFactory.instance.objectNode();
    }
    
    public DynamoSurveyQuestion(DynamoSurveyQuestion question) {
        setSurveyCompoundKey(question.getSurveyCompoundKey());
        setGuid(question.getGuid());
        setIdentifier(question.getIdentifier());
        setOrder(question.getOrder());
        // It should be impossible for this to be null.
        setData(question.getData().deepCopy());
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

    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    @JsonIgnore
    public ObjectNode getData() {
        if (prompt != null) {
            data.put(PROMPT_PROPERTY, prompt);    
        }
        return data;
    }

    public void setData(ObjectNode data) {
        if (data != null) {
            this.data = data;    
        }
    }

    // These are all "synthetic" properties. They look like properties of a question, but in 
    // DynamoDB they are all stored as part of the JSON in the data column. They are lazily
    // converted to Java objects, and added back to the data JsonNode when it is retrieved
    // from this object, this allows statements like the following to work as expected:
    // survey.getIntegerQuestion().getConstraints().getRules().add(...);

    private String prompt; 
    
    @Override
    @DynamoDBIgnore
    public String getPrompt() {
        if (prompt == null) {
            prompt = JsonUtils.asText(data, PROMPT_PROPERTY);
        }
        if (hint != null) {
            data.put(UI_HINTS_PROPERTY, hint.name().toLowerCase());    
        }
        if (constraints != null) {
            data.put(CONSTRAINTS_PROPERTY, mapper.valueToTree(constraints));    
        }
        return prompt;
    }
    
    @Override
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    private UIHint hint;
    
    @Override
    @DynamoDBIgnore
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    public UIHint getUiHint() {
        if (hint == null) {
            hint = JsonUtils.asUIHint(data, UI_HINTS_PROPERTY);
        }
        return hint;
    }
    
    @Override
    public void setUiHint(UIHint hint) {
        this.hint = hint;
    }
    
    private Constraints constraints;
    
    @Override
    @DynamoDBIgnore
    public Constraints getConstraints() {
        if (constraints == null) {
            constraints = JsonUtils.asConstraints(data, CONSTRAINTS_PROPERTY, DATA_TYPE_PROPERTY, ENUM_PROPERTY);
        }
        return constraints;
    }
    
    @Override
    public void setConstraints(Constraints constraints) {
        this.constraints = constraints;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getData() == null) ? 0 : getData().hashCode());
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
        result = prime * result + order;
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
        if (getData() == null) {
            if (other.getData() != null)
                return false;
        } else if (!getData().equals(other.getData()))
            return false;
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
        return true;
    }
    
    @Override
    public String toString() {
        return "DynamoSurveyQuestion [surveyCompoundKey=" + surveyCompoundKey + ", guid=" + guid + ", identifier="
                + identifier + ", order=" + order + ", data=" + getData() + "]";
    }

}
