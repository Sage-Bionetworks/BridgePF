package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import controllers.StudyControllerService;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSurveyDaoTest {

    @Resource
    DynamoSurveyDao surveyDao;
    
    @Resource
    StudyControllerService studyService;
    
    @Before
    public void before() {
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        DynamoTestUtil.clearTable(DynamoSurvey.class, "identifier", "name", "ownerGroup",
                "published", "version", "versionedOn");
        DynamoTestUtil.clearTable(DynamoSurveyQuestion.class, "order", "identifier", "data");
    }

    /*
    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoSurvey.class, "identifier", "name", "ownerGroup",
                "published", "version", "versionedOn");
        DynamoTestUtil.clearTable(DynamoSurveyQuestion.class, "order", "identifier", "data");
    }
    */
    
    @Test(expected=BridgeServiceException.class)
    public void createPreventsEmptyStudyKey() {
        Survey survey = new DynamoSurvey();
        survey.setIdentifier("AAA");
        surveyDao.createSurvey(survey);
    }
    
    @Test(expected=BridgeServiceException.class)
    public void createPreventsNoIdentifier() {
        Survey survey = new DynamoSurvey();
        survey.setStudyKey("AAA");
        surveyDao.createSurvey(survey);
    }
    
    @Test(expected=BridgeServiceException.class)
    public void createPreventsQuestionWithNoIdentifier() {
        Survey survey = new DynamoSurvey();
        survey.setStudyKey("AAA");
        SurveyQuestion question = new DynamoSurveyQuestion();
        survey.getQuestions().add(question);
        surveyDao.createSurvey(survey);
    }
    
    @Test(expected=BridgeServiceException.class)
    public void createPreventsQuestionWithNoSurveyGuid() {
        Survey survey = new DynamoSurvey();
        survey.setStudyKey("AAA");
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("AAA");
        surveyDao.createSurvey(survey);
    }
    
    @Test
    public void crudSurvey() {
        Survey survey = new DynamoSurvey();
        survey.setName("Health Overview Test Survey");
        survey.setIdentifier("overview");
        survey.setOwnerGroup("testResearchers");
        survey.setStudyKey(TestConstants.SECOND_STUDY.getKey());
        
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("age");
        question.setData(JsonNodeFactory.instance.objectNode());
        survey.getQuestions().add(question);
        
        question = new DynamoSurveyQuestion();
        question.setIdentifier("gender");
        survey.getQuestions().add(question);
        
        survey = surveyDao.createSurvey(survey);
        
        assertTrue("Survey has a guid", survey.getGuid() != null);
        assertTrue("Survey has been versioned", survey.getVersionedOn() != 0L);
        assertTrue("Question #1 has a guid", survey.getQuestions().get(0).getGuid() != null);
        assertTrue("Question #2 has a guid", survey.getQuestions().get(0).getGuid() != null);
        
        survey.setIdentifier("newIdentifier");
        surveyDao.updateSurvey(survey);
        survey = surveyDao.getSurvey(survey.getStudyKey(), survey.getGuid());
        assertEquals("Identifier has been changed", "newIdentifier", survey.getIdentifier());
        
        surveyDao.deleteSurvey(survey.getStudyKey(), survey.getGuid());
        
        survey = surveyDao.getSurvey(survey.getStudyKey(), survey.getGuid());
        assertNull(survey);
    }
    
    @Test
    public void crudSurveyQuestions() {
        Survey survey = new DynamoSurvey();
        survey.setName("Health Overview Test Survey");
        survey.setIdentifier("overview");
        survey.setOwnerGroup("testResearchers");
        survey.setStudyKey(TestConstants.SECOND_STUDY.getKey());
        
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("age");
        question.setData(JsonNodeFactory.instance.objectNode());
        survey.getQuestions().add(question);
        
        question = new DynamoSurveyQuestion();
        question.setIdentifier("gender");
        survey.getQuestions().add(question);
        
        survey = surveyDao.createSurvey(survey);
        
        // Now, alter these, and verify they are altered
        survey.getQuestions().get(0).setIdentifier("new age");
        survey.getQuestions().remove(1);
        surveyDao.updateSurvey(survey);
        
        survey = surveyDao.getSurvey(survey.getStudyKey(), survey.getGuid());

        assertEquals("Survey only has one question", 1, survey.getQuestions().size());
        assertEquals("Survey has updated the one question's identifier", "new age", survey.getQuestions().get(0)
                .getIdentifier());
    }

}
