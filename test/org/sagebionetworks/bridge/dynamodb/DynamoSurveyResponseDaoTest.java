package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.SurveyRoundtripSerializationTest;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSurveyResponseDaoTest {
    
    private static final String HEALTH_DATA_CODE = "AAA";

    @Resource
    DynamoSurveyResponseDao surveyResponseDao;
    
    @Resource
    DynamoSurveyDao surveyDao;
    
    private Survey survey;

    @BeforeClass
    public static void initialSetUp() {
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        DynamoTestUtil.clearTable(DynamoSurveyResponse.class, "surveyGuid", "surveyVersionedOn", "healthCode",
                "startedOn", "completedOn", "version", "data");
    }

    /*
    @AfterClass
    public static void finalCleanUp() {
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        DynamoTestUtil.clearTable(DynamoSurveyResponse.class, "surveyGuid", "surveyVersionedOn", "healthCode",
                "startedOn", "completedOn", "version", "data");
    }
    */
    
    @Before
    public void before() throws Exception {
        SurveyRoundtripSerializationTest test = new SurveyRoundtripSerializationTest();
        survey = test.getCompleteSurvey();
        makeSurveyNew(survey);
        survey = surveyDao.createSurvey(survey);
    }
    
    private void makeSurveyNew(Survey survey) {
        survey.setGuid(null);
        survey.setPublished(false);
        survey.setVersion(null);
        survey.setVersionedOn(0L);
        for (SurveyQuestion question : survey.getQuestions()) {
            question.setGuid(null);
        }
    }
    
    @After
    public void after() {
        surveyDao.deleteSurvey(survey.getGuid(), survey.getVersionedOn());
        survey = null;
    }
    
    @Test
    public void createSurveyResponse() {
        List<SurveyAnswer> answers = Lists.newArrayList();
        
        SurveyResponse response = surveyResponseDao.createSurveyResponse(survey.getGuid(), survey.getVersionedOn(), HEALTH_DATA_CODE, answers);
        assertTrue("Has been assigned a GUID", response.getGuid() != null);
        
        SurveyResponse newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());
        
        assertEquals("Has right guid", response.getGuid(), newResponse.getGuid());
        assertEquals("Has right startedOn", response.getStartedOn(), newResponse.getStartedOn());
        assertTrue("and startedOn isn't 0", newResponse.getStartedOn() > 0L);
        assertEquals("Has right completedOn", response.getCompletedOn(), newResponse.getCompletedOn());
        assertTrue("and completedOn isn't 0", newResponse.getCompletedOn() > 0L);
        
        // Now push some answers through the answer API
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnswer(Boolean.FALSE);
        answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answer.setClient("mobile");
        answer.setQuestionGuid(survey.getQuestions().get(0).getGuid());
        answers.add(answer);
        
        answer = new SurveyAnswer();
        answer.setAnswer(DateUtils.getCurrentMillisFromEpoch());
        answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answer.setClient("mobile");
        answer.setQuestionGuid(survey.getQuestions().get(1).getGuid());
        answers.add(answer);
        
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        
        newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());
        assertEquals("Now the response has two answers", 2, newResponse.getAnswers().size());
        
        // You can't append answers to questions that have already been answered.
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());
        assertEquals("The response continues to have two answers", 2, newResponse.getAnswers().size());
        
        // But if you update a timestamp, it looks new and it will be updated.
        answers.get(0).setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answers.get(0).setAnswer(Boolean.TRUE);
        surveyResponseDao.appendSurveyAnswers(newResponse, answers);
        newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());
        
        newResponse = surveyResponseDao.getSurveyResponse(response.getGuid());
        assertEquals("Answer was updated due to more recent timestamp", Boolean.TRUE, answers.get(0).getAnswer());
        
        // delete it
        surveyResponseDao.deleteSurveyResponse(response);
        try {
            surveyResponseDao.getSurveyResponse(response.getGuid());
            fail("Should have thrown an EntityNotFoundException");
        } catch(EntityNotFoundException nfe) {
            
        }
    }
    
}
