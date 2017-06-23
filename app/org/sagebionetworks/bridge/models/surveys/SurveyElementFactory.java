package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.SurveyElementConstants.SURVEY_QUESTION_TYPE;
import static org.sagebionetworks.bridge.models.surveys.SurveyElementConstants.SURVEY_INFO_SCREEN_TYPE;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The ugliness that would be handled by a full ORM solution's object hierarchy 
 * mapping to the DynamoDB table.
 */
public class SurveyElementFactory {
    
    public static SurveyElement fromJson(JsonNode node) {
        String type = JsonUtils.asText(node, "type");
        if (SURVEY_QUESTION_TYPE.equals(type)) {
            return SurveyQuestion.fromJson(node);
        } else if (SURVEY_INFO_SCREEN_TYPE.equals(type)) {
            return SurveyInfoScreen.fromJson(node);
        } else {
            throw new InvalidEntityException("Survey element type '"+type+"' not recognized.");
        }
    }

    public static SurveyElement fromDynamoEntity(SurveyElement element) {
        if (element.getType().equals(SURVEY_QUESTION_TYPE)) {
            return new DynamoSurveyQuestion(element);
        } else if (element.getType().equals(SURVEY_INFO_SCREEN_TYPE)) {
            return new DynamoSurveyInfoScreen(element);
        } else {
            throw new InvalidEntityException("Survey element type '"+element.getType()+"' not recognized.");
        }
    }

}
