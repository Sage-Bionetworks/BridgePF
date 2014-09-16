package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;

import org.junit.Ignore;
import org.junit.Test;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse.Status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

public class DynamoSurveyResponseTest {

    @Test
    @Ignore
    public void correctlyDeterminesStatus() {
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        assertEquals("Survey has not been started", Status.UNSTARTED);
        
        response.setStartedOn(DateUtils.getCurrentMillisFromEpoch());
        assertEquals("Survey is in progress", Status.IN_PROGRESS);
        
        response.setCompletedOn(DateUtils.getCurrentMillisFromEpoch());
        assertEquals("Survey has been finished", Status.FINISHED);
    }
    
    @Test
    public void canRountripSerializeSurveyResponse() throws Exception {
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setStartedOn(DateUtils.getCurrentMillisFromEpoch());
        response.setGuid(UUID.randomUUID().toString());
        response.setSurveyGuid(UUID.randomUUID().toString());
        response.setSurveyVersionedOn(DateUtils.getCurrentMillisFromEpoch());
        response.setHealthCode(UUID.randomUUID().toString());
        response.setVersion(2L);
        response.setCompletedOn(DateUtils.getCurrentMillisFromEpoch());
        
        List<SurveyAnswer> answers = Lists.newArrayList();
        addFifteenQuestions(answers);
        response.setAnswers(answers);
        
        String string = JsonUtils.toJSON(response);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(string);
        DynamoSurveyResponse newResponse = DynamoSurveyResponse.fromJson(node);
        // These are not copied over
        newResponse.setGuid(response.getGuid());        
        newResponse.setSurveyGuid(response.getSurveyGuid());
        newResponse.setSurveyVersionedOn(response.getSurveyVersionedOn());

        assertEquals("Survey response serialized/deserialized correctly", response.hashCode(), newResponse.hashCode());
    }
    
    private void addFifteenQuestions(List<SurveyAnswer> answers) {
        for (int i=0; i < 15; i++) {
            SurveyAnswer answer = new SurveyAnswer();
            answer.setAnswer("let's assume that answers can be somewhat long");
            answer.setClient("mobile");
            answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
            answer.setQuestionGuid(UUID.randomUUID().toString());
            answers.add(answer);
        }
    }

}
