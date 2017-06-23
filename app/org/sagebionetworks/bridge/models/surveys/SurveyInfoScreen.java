package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSurveyInfoScreen.class)
@BridgeTypeName("SurveyInfoScreen")
public interface SurveyInfoScreen extends SurveyElement {
    /** Convenience method for creating an instance using a concrete implementation. */
    static SurveyInfoScreen create() {
        return new DynamoSurveyInfoScreen();
    }
    
    static DynamoSurveyInfoScreen fromJson(JsonNode node) {
        DynamoSurveyInfoScreen question = new DynamoSurveyInfoScreen();
        question.setType( JsonUtils.asText(node, TYPE_PROPERTY) );
        question.setIdentifier( JsonUtils.asText(node, IDENTIFIER_PROPERTY) );
        question.setGuid( JsonUtils.asText(node, GUID_PROPERTY) );
        question.setPrompt(JsonUtils.asText(node, PROMPT_PROPERTY));
        question.setPromptDetail(JsonUtils.asText(node, PROMPT_DETAIL_PROPERTY));
        question.setTitle(JsonUtils.asText(node, TITLE_PROPERTY));
        question.setImage(JsonUtils.asEntity(node, IMAGE_PROPERTY, Image.class));
        question.setBeforeRules(JsonUtils.asEntityList(node, BEFORE_RULES_PROPERTY, SurveyRule.class));
        question.setAfterRules(JsonUtils.asEntityList(node, AFTER_RULES_PROPERTY, SurveyRule.class));
        return question;
    }
    
    String getTitle();
    void setTitle(String title);
    
    String getPrompt();
    void setPrompt(String prompt);
    
    String getPromptDetail();
    void setPromptDetail(String promptDetail);

    Image getImage();
    void setImage(Image image);
    
}
