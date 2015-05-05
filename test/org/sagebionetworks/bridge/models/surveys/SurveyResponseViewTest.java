package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class SurveyResponseViewTest {

    @Test
    public void unwrapsSurveyCorrectly() throws Exception {
        DynamoSurvey survey = new DynamoSurvey();
        survey.setGuid(BridgeUtils.generateGuid());
        survey.setIdentifier("identifier");
        survey.setName("Name");
        survey.setPublished(true);
        survey.setStudyIdentifier("api");
        survey.setVersion(2L);
        survey.setCreatedOn(DateTime.now().minusHours(3).getMillis());
        survey.setModifiedOn(DateTime.now().minusHours(1).getMillis());
        
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setCompletedOn(DateTime.now().getMillis());
        response.setStartedOn(DateTime.now().minusHours(1).getMillis());
        response.setHealthCode("healthCode");
        response.setSurveyKey("BBB:"+DateTime.now().getMillis());
        response.setIdentifier(BridgeUtils.generateGuid());
        response.setVersion(2L);
        
        SurveyResponseView view = new SurveyResponseView(response, survey);
        
        String json = BridgeObjectMapper.get().writeValueAsString(view);
        
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertTrue(node.has("identifier"));
        assertTrue(node.has("startedOn"));
        assertTrue(node.has("completedOn"));
        assertTrue(node.has("answers"));
        assertTrue(node.has("status"));
        assertTrue(node.has("survey"));
        assertEquals("SurveyResponse", node.get("type").asText());
        
    }
    
}
