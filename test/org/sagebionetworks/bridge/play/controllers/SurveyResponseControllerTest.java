package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.services.SurveyResponseService;

import play.core.j.JavaResultExtractor;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class SurveyResponseControllerTest {
    
    private static final String HEALTH_CODE = "BBB";
    
    private static final String SURVEY_RESPONSE_IDENTIFIER = "CCC";
    
    private static final GuidCreatedOnVersionHolder KEYS = new GuidCreatedOnVersionHolderImpl("AAA", 
            DateTime.parse("2010-10-10").getMillis());
    
    private SurveyResponseService service;
    
    private SurveyResponseController controller;

    @Before
    public void before() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE).build();
        UserSession session = new UserSession(participant);
        session.setStudyParticipant(participant);
        
        service = mock(SurveyResponseService.class);
        
        controller = spy(new SurveyResponseController());
        controller.setSurveyResponseService(service);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
    }
    
    private void setContext(Object object) throws Exception {
        String json = BridgeObjectMapper.get().writeValueAsString(object);
        TestUtils.mockPlayContextWithJson(json);
    }
    
    private SurveyResponse mockSurveyResponse() {
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setSurveyKey(KEYS);
        response.setIdentifier(SURVEY_RESPONSE_IDENTIFIER);
        
        SurveyAnswer answer = new SurveyAnswer();
        answer.setQuestionGuid("DDD");
        answer.setAnswers(Lists.newArrayList("A", "B"));
        answer.setAnsweredOn(DateTime.parse("2010-10-09").getMillis());
        answer.setClient("mobile");
        response.getAnswers().add(answer);
        
        return response;
    }
    
    private <T> T resultToType(Result result, Class<? extends T> clazz) throws Exception {
        byte[] content = JavaResultExtractor.getBody(result, 0L);
        return BridgeObjectMapper.get().readValue(content, clazz);
    }
    
    private JsonNode resultToJSON(Result result) throws Exception {
        byte[] content = JavaResultExtractor.getBody(result, 0L);
        return BridgeObjectMapper.get().readTree(content);
    }
    
    @Test
    public void createSurveyResponse() throws Exception {
        Survey survey = new TestSurvey(SurveyResponseControllerTest.class, false);
        SurveyResponse response = mockSurveyResponse();
        SurveyResponseView view = new SurveyResponseView(response, survey);
        setContext(response);
        
        when(service.createSurveyResponse(KEYS, HEALTH_CODE, response.getAnswers(), SURVEY_RESPONSE_IDENTIFIER)).thenReturn(view);
        
        Result result = controller.createSurveyResponse();
        IdentifierHolder holder = resultToType(result, IdentifierHolder.class);

        verify(service).createSurveyResponse(KEYS, HEALTH_CODE, response.getAnswers(), SURVEY_RESPONSE_IDENTIFIER);
        assertEquals(SURVEY_RESPONSE_IDENTIFIER, holder.getIdentifier());
        assertEquals(201, result.status()); // created
    }
    
    @Test(expected = InvalidEntityException.class)
    public void surveyResponseWithoutKeys() throws Exception {
        SurveyResponse response = mockSurveyResponse();
        response.setSurveyGuid(null);
        setContext(response);
        
        controller.createSurveyResponse();
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void createSurveyResponseWithoutAnIdentifier() throws Exception {
        Survey survey = new TestSurvey(SurveyResponseControllerTest.class, false);
        SurveyResponse response = mockSurveyResponse();
        response.setIdentifier(null);
        setContext(response);
        
        SurveyResponseView view = new SurveyResponseView(mockSurveyResponse(), survey);
        when(service.createSurveyResponse(any(GuidCreatedOnVersionHolderImpl.class), anyString(), any(List.class), anyString())).thenReturn(view);
        
        Result result = controller.createSurveyResponse();
        JsonNode node = resultToJSON(result);
        
        assertEquals(SURVEY_RESPONSE_IDENTIFIER, node.get("identifier").asText());
    }
    
    @Test
    public void getSurveyResponse() throws Exception {
        Survey survey = new TestSurvey(SurveyResponseControllerTest.class, false);
        SurveyResponse response = mockSurveyResponse();
        SurveyResponseView view = new SurveyResponseView(response, survey);
        
        when(service.getSurveyResponse(HEALTH_CODE, SURVEY_RESPONSE_IDENTIFIER)).thenReturn(view);
        
        Result result = controller.getSurveyResponse(SURVEY_RESPONSE_IDENTIFIER);
        JsonNode node = resultToJSON(result); 
        
        assertEquals(SURVEY_RESPONSE_IDENTIFIER, node.get("identifier").asText());
        assertEquals(1, ((ArrayNode)node.get("answers")).size());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void appendAnswersToSurveyResponse() throws Exception {
        Survey survey = new TestSurvey(SurveyResponseControllerTest.class, false);
        SurveyResponse response = mockSurveyResponse();
        SurveyResponseView view = new SurveyResponseView(response, survey);
        setContext(response);
        
        when(controller.getSurveyResponseIfAuthorized(SURVEY_RESPONSE_IDENTIFIER)).thenReturn(view);
        
        controller.appendSurveyAnswers(SURVEY_RESPONSE_IDENTIFIER);
        
        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        verify(service).appendSurveyAnswers(any(SurveyResponse.class), argument.capture());
        
        assertEquals(response.getAnswers().get(0), argument.getValue().get(0));
    }

}
