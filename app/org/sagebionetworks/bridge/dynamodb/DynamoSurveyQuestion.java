package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DynamoSurveyQuestion extends DynamoSurveyElement implements SurveyQuestion {
    
    private static final String CONSTRAINTS_PROPERTY = "constraints";
    private static final String UI_HINTS_PROPERTY = "uiHint";
    private static final String PROMPT_PROPERTY = "prompt";
    private static final String PROMPT_DETAIL_PROPERTY = "promptDetail";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String GUID_PROPERTY = "guid";
    private static final String TYPE_PROPERTY = "type";
    
    public static SurveyQuestion fromJson(JsonNode node) {
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setType( JsonUtils.asText(node, TYPE_PROPERTY) );
        question.setIdentifier( JsonUtils.asText(node, IDENTIFIER_PROPERTY) );
        question.setGuid( JsonUtils.asText(node, GUID_PROPERTY) );
        question.setPrompt(JsonUtils.asText(node, PROMPT_PROPERTY));
        question.setPromptDetail(JsonUtils.asText(node, PROMPT_DETAIL_PROPERTY));
        question.setUiHint(JsonUtils.asUIHint(node, UI_HINTS_PROPERTY));
        question.setConstraints(JsonUtils.asConstraints(node, CONSTRAINTS_PROPERTY));
        return question;
    }

    private String prompt;
    private String promptDetail;
    private UIHint hint;
    private Constraints constraints;

    public DynamoSurveyQuestion() {
        setType("SurveyQuestion");
    }
    
    public DynamoSurveyQuestion(SurveyElement entry) {
        setType( entry.getType() );
        setIdentifier( entry.getIdentifier() );
        setGuid( entry.getGuid() );
        setData( entry.getData() );
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
    public String getPromptDetail() {
        return promptDetail;
    }
    
    @Override
    public void setPromptDetail(String promptDetail) {
        this.promptDetail = promptDetail;
    }

    @Override
    @DynamoDBIgnore
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

    @Override
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    @JsonIgnore
    public JsonNode getData() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put(PROMPT_PROPERTY, prompt);
        data.put(PROMPT_DETAIL_PROPERTY, promptDetail);
        data.put(UI_HINTS_PROPERTY, hint.name().toLowerCase());    
        data.set(CONSTRAINTS_PROPERTY, BridgeObjectMapper.get().valueToTree(constraints));
        return data;
    }

    @Override
    public void setData(JsonNode data) {
        this.prompt = JsonUtils.asText(data, PROMPT_PROPERTY);
        this.promptDetail = JsonUtils.asText(data, PROMPT_DETAIL_PROPERTY);
        this.hint = JsonUtils.asUIHint(data, UI_HINTS_PROPERTY);
        this.constraints = JsonUtils.asConstraints(data, CONSTRAINTS_PROPERTY);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(constraints);
        result = prime * result + Objects.hashCode(hint);
        result = prime * result + Objects.hashCode(prompt);
        result = prime * result + Objects.hashCode(promptDetail);
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final DynamoSurveyQuestion that = (DynamoSurveyQuestion) obj;
        return Objects.equals(constraints, that.constraints) && Objects.equals(hint, that.hint)
            && Objects.equals(prompt, that.prompt)
            && Objects.equals(promptDetail, that.promptDetail);
    }

    @Override
    public String toString() {
        return String.format("DynamoSurveyQuestion [hint=%s, prompt=%s, promptDetail=%s, constraints=%s]", 
            hint, prompt, promptDetail, constraints);
    }

}
