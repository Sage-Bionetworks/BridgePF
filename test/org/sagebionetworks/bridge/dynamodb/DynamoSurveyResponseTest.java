package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.UUID;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse.Status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

public class DynamoSurveyResponseTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(DynamoSurveyResponse.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void correctlyConvertsSurveyToFromKey() {
        long createdOn = DateTime.now().getMillis();
        
        Survey survey = new DynamoSurvey();
        survey.setGuid(BridgeUtils.generateGuid());
        survey.setCreatedOn(createdOn);
        
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setSurveyKey(survey);
        
        String key = String.format("%s:%d", survey.getGuid(), survey.getCreatedOn());
        assertEquals(1, StringUtils.countMatches(key, ":"));
        assertEquals(key, response.getSurveyKey());
        assertEquals(survey.getGuid(), response.getSurveyGuid());
        assertEquals(survey.getCreatedOn(), response.getSurveyCreatedOn());
    }
    
    @Test
    public void correctlyDeterminesStatus() {
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        assertEquals("Survey has not been started", Status.UNSTARTED, response.getStatus());
        
        response.setStartedOn(DateUtils.getCurrentMillisFromEpoch());
        assertEquals("Survey is in progress", Status.IN_PROGRESS, response.getStatus());
        
        response.setCompletedOn(DateUtils.getCurrentMillisFromEpoch());
        assertEquals("Survey has been finished", Status.FINISHED, response.getStatus());
    }
    
    @Test
    public void canRountripSerializeSurveyResponse() throws Exception {
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setStartedOn(DateUtils.getCurrentMillisFromEpoch());
        response.setIdentifier(UUID.randomUUID().toString());
        response.setSurveyKey(BridgeUtils.generateGuid()+":"+DateUtils.getCurrentMillisFromEpoch());
        response.setHealthCode(UUID.randomUUID().toString());
        response.setVersion(2L);
        response.setCompletedOn(DateUtils.getCurrentMillisFromEpoch());
        
        List<SurveyAnswer> answers = Lists.newArrayList();
        addFifteenQuestions(answers);
        response.setAnswers(answers);
        
        ObjectMapper mapper = BridgeObjectMapper.get();
        String string = mapper.writeValueAsString(response);
        
        DynamoSurveyResponse newResponse = mapper.readValue(string, DynamoSurveyResponse.class);
        response.setSurveyKey(newResponse.getSurveyKey());
        
        assertNull(newResponse.getVersion());
        assertNull(newResponse.getHealthCode());
        
        // These are not copied over
        newResponse.setVersion(response.getVersion());
        newResponse.setHealthCode(response.getHealthCode());

        assertEquals("Survey response serialized/deserialized correctly", response.toString(), newResponse.toString());
    }
    
    private void addFifteenQuestions(List<SurveyAnswer> answers) {
        for (int i=0; i < 15; i++) {
            SurveyAnswer answer = new SurveyAnswer();
            answer.addAnswer("let's assume that answers can be somewhat long");
            answer.setClient("mobile");
            answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
            answer.setQuestionGuid(UUID.randomUUID().toString());
            answers.add(answer);
        }
    }

}
