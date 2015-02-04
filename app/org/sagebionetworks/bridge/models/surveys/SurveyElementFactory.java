package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The ugliness that would be handled by a full ORM solution's object hierarchy 
 * mapping to the DynamoDB table.
 */
public class SurveyElementFactory {
    
    public static SurveyElement fromJson(JsonNode node) {
        String type = JsonUtils.asText(node, "type");
        if (SurveyElement.SURVEY_QUESTION_TYPE.equals(type)) {
            return DynamoSurveyQuestion.fromJson(node);
        } else if (SurveyElement.SURVEY_INFO_SCREEN_TYPE.equals(type)) {
            return DynamoSurveyInfoScreen.fromJson(node);
        } else {
            throw new BridgeServiceException("Survey element type '"+type+"' not recognized.");
        }
    }

    public static SurveyElement fromDynamoEntity(SurveyElement element) {
        if (element.getType().equals(SurveyElement.SURVEY_QUESTION_TYPE)) {
            return new DynamoSurveyQuestion(element);
        } else if (element.getType().equals(SurveyElement.SURVEY_INFO_SCREEN_TYPE)) {
            return new DynamoSurveyInfoScreen(element);
        } else {
            throw new BridgeServiceException("Survey element type '"+element.getType()+"' not recognized.");
        }
    }

}
