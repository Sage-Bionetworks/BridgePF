package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponseDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;

import com.google.common.collect.Lists;

public class SurveyResponseServiceImplTest {

    private SurveyResponseServiceImpl service;
    
    private DynamoSurveyResponseDao surveyResponseDao;
    
    private DynamoSurveyDao surveyDao;
    
    private Survey survey;

    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        survey = getSurvey();
        
        service = new SurveyResponseServiceImpl();
        
        surveyDao = mock(DynamoSurveyDao.class);
        when(surveyDao.getSurvey(any(GuidCreatedOnVersionHolder.class))).thenReturn(survey);
        service.setSurveyDao(surveyDao);

        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setSurveyKey(survey);
        
        surveyResponseDao = mock(DynamoSurveyResponseDao.class);
        when(surveyResponseDao.appendSurveyAnswers(any(SurveyResponse.class), any(List.class))).thenReturn(response);
        when(surveyResponseDao.getSurveyResponse(anyString(), anyString())).thenReturn(response);
        
        service.setSurveyResponseDao(surveyResponseDao);
    }
    
    @Test
    public void createSurveyResponseNoSurveyGuid() {
        try {
            survey.setGuid(null);
            service.createSurveyResponse(survey, "healthCode", getAnswers());
            fail("Should have thrown exception");
        } catch(Throwable t) {
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    
    @Test
    public void createSurveyResponseNoSurveyCreatedOn() {
        try {
            survey.setCreatedOn(0L);
            service.createSurveyResponse(survey, "healthCode", getAnswers());
            fail("Should have thrown exception");
        } catch(Throwable t) {
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    
    @Test
    public void createSurveyResponseNoIdentifier() {
        try {
            service.createSurveyResponse(survey, "healthCode", getAnswers(), null);
            fail("Should have thrown exception");
        } catch(Throwable t) {
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    
    @Test
    public void createSurveyResponseBlankHealthCode() {
        try {
            service.createSurveyResponse(survey, "   ", getAnswers());
            fail("Should have thrown exception");
        } catch(Throwable t) {
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    
    @Test
    public void createSurveyResponseNoAnswers() {
        try {
            service.createSurveyResponse(survey, "healthCode", null);
            fail("Should have thrown exception");
        } catch(Throwable t) {
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    
    @Test
    public void createSurveyWithInvalidAnswers() {
        try {
            List<SurveyAnswer> answers = getAnswers();
            answers.get(0).setQuestionGuid(null);
            
            service.createSurveyResponse(survey, "healthCode", answers);
            fail("Should have thrown exception");
        } catch(Throwable t) {
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void canCreateSurveyResponse() throws Exception {
        
        when(surveyResponseDao.createSurveyResponse(
            any(GuidCreatedOnVersionHolder.class), any(String.class), any(List.class), any(String.class))).thenReturn(getSurveyResponse());
        
        SurveyResponseView response = service.createSurveyResponse(survey, "healthCode", getAnswers());
        
        assertNotNull(response);
        assertNotNull(response.getSurvey());
        assertNotNull(response.getIdentifier());
        assertNotNull(response.getAnswers());
        assertEquals("healthCode", response.getResponse().getHealthCode());
        assertEquals((Long)2L, (Long)response.getVersion());
        
        verify(surveyDao).getSurvey(any(GuidCreatedOnVersionHolder.class));
        verify(surveyResponseDao).createSurveyResponse(
            any(GuidCreatedOnVersionHolder.class), any(String.class), any(List.class), any(String.class));
        verifyNoMoreInteractions(surveyDao);
        verifyNoMoreInteractions(surveyResponseDao);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void canCreateSurveyResponseWithIdentifier() throws Exception {
        when(surveyResponseDao.createSurveyResponse(
            any(GuidCreatedOnVersionHolder.class), any(String.class), any(List.class), any(String.class))).thenReturn(getSurveyResponse());
        
        SurveyResponseView response = service.createSurveyResponse(survey, "healthCode", getAnswers(), "belgium");
        
        assertNotNull(response);
        assertNotNull(response.getSurvey());
        assertNotNull(response.getIdentifier());
        assertNotNull(response.getAnswers());
        assertEquals("healthCode", response.getResponse().getHealthCode());
        assertEquals("belgium", response.getIdentifier());
        assertEquals((Long)2L, (Long)response.getVersion());
        
        verify(surveyDao).getSurvey(any(GuidCreatedOnVersionHolder.class));
        verify(surveyResponseDao).createSurveyResponse(
            any(GuidCreatedOnVersionHolder.class), any(String.class), any(List.class), any(String.class));
        verifyNoMoreInteractions(surveyDao);
        verifyNoMoreInteractions(surveyResponseDao);
    }

    @Test
    public void getSurveyResponseNoHealthCode() {
        try {
            service.getSurveyResponse(null, "identifier");
            fail("Should have thrown exception");
        } catch(Throwable t) {
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    
    @Test
    public void getSurveyResponseNoIdentifier() {
        try {
            service.getSurveyResponse("healthCode", null);
            fail("Should have thrown exception");
        } catch(Throwable t) {
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    
    @Test
    public void getSurveyResponseWorks() {
        service.getSurveyResponse("healthCode", "identifier");
        
        verify(surveyResponseDao).getSurveyResponse("healthCode", "identifier");
        verify(surveyDao).getSurvey(any(GuidCreatedOnVersionHolder.class));
        verifyNoMoreInteractions(surveyDao);
        verifyNoMoreInteractions(surveyResponseDao);
    }
    
    @Test
    public void appendSurveyAnswerNoResponse() {
        try {
            service.appendSurveyAnswers(null, getAnswers());
            fail("Should have thrown an exception");
        } catch(Throwable throwable) {
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    @Test
    public void appendSurveyAnswerNoAnswers() {
        try {
            service.appendSurveyAnswers(getSurveyResponse(), null);
            fail("Should have thrown an exception");
        } catch(Throwable throwable) {
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }
    }
    @Test
    public void appendSurveyAnswersThatAreInvalid() {
        List<SurveyAnswer> answers = getAnswers();
        // fuzz the answer
        answers.get(0).getAnswers().set(0, "Belgium");
        try {
            service.appendSurveyAnswers(getSurveyResponse(), answers);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("Belgium is not a valid integer"));
            verify(surveyDao).getSurvey(any(GuidCreatedOnVersionHolder.class));
            verifyNoMoreInteractions(surveyDao);
            verifyNoMoreInteractions(surveyResponseDao);
        }        
    }
    @Test
    public void appendSurveyAnswersWorks() {
        List<SurveyAnswer> answers = getAnswers();
        SurveyResponse response = getSurveyResponse();
        
        service.appendSurveyAnswers(response, answers);
        
        verify(surveyResponseDao).appendSurveyAnswers(response, answers);
        verify(surveyDao).getSurvey(any(GuidCreatedOnVersionHolder.class));
        verifyNoMoreInteractions(surveyDao);
        verifyNoMoreInteractions(surveyResponseDao);
    }
    
    @Test
    public void deleteSurveyResponses() {
        service.deleteSurveyResponses("healthCode");
        
        verify(surveyResponseDao).deleteSurveyResponses("healthCode");
        verifyNoMoreInteractions(surveyDao);
        verifyNoMoreInteractions(surveyResponseDao);
    }
    
    @Test
    public void deleteSurveyResponseNoHealthCode() {
        try {
            service.deleteSurveyResponses(null);
            fail("Should have thrown exception");
        } catch(Exception e) {
            //
        }
        verifyNoMoreInteractions(surveyDao);
        verifyNoMoreInteractions(surveyResponseDao);
    }
    
    private DynamoSurveyResponse getSurveyResponse() {
        DynamoSurveyResponse response = new DynamoSurveyResponse();
        response.setHealthCode("healthCode");
        response.setIdentifier("belgium");
        response.setStartedOn(DateTime.now().getMillis());
        response.setSurveyKey("BBB:"+DateTime.now().getMillis());
        response.setAnswers(getAnswers());
        response.setVersion(2L);
        return response;
    }
    
    private DynamoSurvey getSurvey() {
        DynamoSurvey survey = new DynamoSurvey();
        survey.setCreatedOn(DateTime.now().getMillis());
        survey.setGuid(BridgeUtils.generateGuid());
        survey.setIdentifier("identifier");
        survey.setName("A Survey");
        survey.setPublished(true);
        survey.setStudyIdentifier("api");
        survey.setVersion(2L);
        
        List<SurveyElement> elements = Lists.newArrayList();
        DynamoSurveyQuestion element = new DynamoSurveyQuestion();
        element.setConstraints(new IntegerConstraints());
        element.setIdentifier("age");
        element.setGuid(BridgeUtils.generateGuid());
        element.setPrompt("What's your age?");
        element.setSurveyKeyComponents(survey.getGuid(), survey.getCreatedOn());
        element.setType("SurveyQuestion");
        elements.add(element);
        survey.setElements(elements);
        return survey;
    }
    
    private List<SurveyAnswer> getAnswers() {
        List<SurveyAnswer> answers = Lists.newArrayList();
        
        SurveyAnswer answer = new SurveyAnswer();
        answer.setQuestionGuid(survey.getElements().get(0).getGuid());
        answer.setAnsweredOn(DateTime.now().getMillis());
        answer.setAnswers(Lists.newArrayList("24"));
        answer.setClient("mobile");
        answer.setDeclined(false);
        answers.add(answer);
        return answers;
    }
    
}
