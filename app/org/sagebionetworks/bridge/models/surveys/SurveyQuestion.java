package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = DynamoSurveyQuestion.class)
@BridgeTypeName("SurveyQuestion")
public interface SurveyQuestion extends SurveyElement {
    /** Convenience method for creating an instance of Survey using a concrete implementation. */
    static SurveyQuestion create() {
        return new DynamoSurveyQuestion();
    }
    
    static SurveyQuestion fromJson(JsonNode node) {
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setType( JsonUtils.asText(node, TYPE_PROPERTY) );
        question.setIdentifier( JsonUtils.asText(node, IDENTIFIER_PROPERTY) );
        question.setGuid( JsonUtils.asText(node, GUID_PROPERTY) );
        question.setPrompt(JsonUtils.asText(node, PROMPT_PROPERTY));
        question.setPromptDetail(JsonUtils.asText(node, PROMPT_DETAIL_PROPERTY));
        question.setFireEvent(JsonUtils.asBoolean(node, FIRE_EVENT_PROPERTY));
        question.setUiHint(JsonUtils.asEntity(node, UI_HINTS_PROPERTY, UIHint.class));
        question.setBeforeRules(JsonUtils.asEntityList(node, BEFORE_RULES_PROPERTY, SurveyRule.class));
        question.setAfterRules(JsonUtils.asEntityList(node, AFTER_RULES_PROPERTY, SurveyRule.class));
        question.setConstraints(JsonUtils.asConstraints(node, CONSTRAINTS_PROPERTY));
        return question;
    }
    
    String getPrompt();

    void setPrompt(String prompt);

    String getPromptDetail();

    void setPromptDetail(String promptDetail);

    boolean getFireEvent();

    void setFireEvent(boolean fireEvent);

    UIHint getUiHint();

    void setUiHint(UIHint hint);

    Constraints getConstraints();

    void setConstraints(Constraints constraints);

}
