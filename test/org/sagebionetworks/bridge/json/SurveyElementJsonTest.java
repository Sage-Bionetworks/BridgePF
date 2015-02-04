package org.sagebionetworks.bridge.json;

import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;

import com.fasterxml.jackson.databind.JsonNode;

public class SurveyElementJsonTest {

    @Test(expected = InvalidEntityException.class)
    public void incorrectTypeThrowsBridgeException() throws Exception {
        SurveyInfoScreen screen = new DynamoSurveyInfoScreen();
        screen.setIdentifier("foo");
        screen.setTitle("Title");
        screen.setPrompt("Prompt");
        screen.setPromptDetail("Prompt detail");
        screen.setType("foo");
        screen.setImageSource("https://pbs.twimg.com/profile_images/1642204340/ReferencePear_400x400.PNG");
        
        DynamoSurvey survey = new DynamoSurvey();
        survey.setIdentifier("test-survey");
        survey.setName("Test study");
        survey.getElements().add(screen);

        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        JsonNode node = mapper.readTree(mapper.writeValueAsString(survey));
        
        DynamoSurvey.fromJson(node);
    }
    
}
