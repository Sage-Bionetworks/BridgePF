package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@DynamoDBTable(tableName = "SurveyQuestion")
public class DynamoSurveyQuestion implements SurveyQuestion, DynamoTable {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private static final String IDENTIFIER_FIELD = "identifier";
    private static final String GUID_FIELD = "guid";
    private static final String DATA_FIELD = "data";
    
    public static SurveyQuestion fromJson(JsonNode node) {
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier( JsonUtils.asText(node, IDENTIFIER_FIELD) );
        question.setGuid( JsonUtils.asText(node, GUID_FIELD) );
        question.setData( JsonUtils.asObjectNode(node, DATA_FIELD) );
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
    
    public DynamoSurveyQuestion(SurveyQuestion question) {
        setSurveyCompoundKey(question.getSurveyCompoundKey());
        setGuid(question.getGuid());
        setIdentifier(question.getIdentifier());
        setOrder(question.getOrder());
        if (question.getData() != null) {
            setData(question.getData().deepCopy());    
        } else {
            this.data = JsonNodeFactory.instance.objectNode();
        }
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
    public ObjectNode getData() {
        return data;
    }

    @Override
    public void setData(ObjectNode data) {
        this.data = data;
    }
    
    @Override
    public String toString() {
        return "DynamoSurveyQuestion [surveyCompoundKey=" + surveyCompoundKey + ", guid=" + guid + ", identifier="
                + identifier + ", order=" + order + ", data=" + data + "]";
    }

    @Override
    @DynamoDBIgnore // stored in data field
    public String getPrompt() {
        return JsonUtils.asText(data, "prompt");
    }
    
    public void setPrompt(String prompt) {
        data.put("prompt", prompt);
    }

    @Override
    @DynamoDBIgnore // stored in data field
    public boolean getDeclined() {
        return JsonUtils.asBoolean(data, "declined");
    }
    
    public void setDeclined(boolean declined) {
        data.put("declined", declined);
    }

    @Override
    @DynamoDBIgnore // stored in data field
    public List<String> getUiHints() {
        return JsonUtils.toStringList(data, "ui-hints");
    }
    
    public void setUiHints(List<String> hints) {
        data.put("ui-hints", JsonUtils.toArrayNode(hints));
    }
    
    @Override
    @DynamoDBIgnore // stored in data field
    public String getType() {
        return JsonUtils.asText(data, "type");
    }
    
    public void setType(String type) {
        data.put("type", type);
        if ("integer".equals(type)) {
            
        }
    }

    private static Map<String,Class<? extends Constraints>> constraintImpls = Maps.newHashMap();
    static {
        constraintImpls.put("integer", IntegerConstraints.class);
    }
    
    @Override
    @DynamoDBIgnore // stored in data field
    public Constraints getConstraints() {
        JsonNode con = data.get("constraints");
        return mapper.readValue(con, Constraints.class);
        return null;
    }

}
