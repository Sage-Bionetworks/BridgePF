package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.PublishedSurveyException;
import org.sagebionetworks.bridge.dao.SurveyNotFoundException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

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

    private Survey createTestSurvey() {
        return createTestSurvey("Health Overview Test Survey");
    }
    
    private Survey createTestSurvey(String name) {
        Survey survey = new DynamoSurvey();
        survey.setName(name);
        survey.setIdentifier("overview");
        survey.setStudyKey(TestConstants.SECOND_STUDY.getKey());
        return survey;
    }
    
    @Test(expected=BridgeServiceException.class)
    @Ignore
    public void createPreventsEmptyStudyKey() {
        Survey survey = createTestSurvey();
        survey.setStudyKey(null);
        surveyDao.createSurvey(survey);
    }
    
    @Test(expected=BridgeServiceException.class)
    @Ignore
    public void createPreventsNoIdentifier() {
        Survey survey = createTestSurvey();
        survey.setIdentifier(null);
        surveyDao.createSurvey(survey);
    }
    
    @Test(expected=BridgeServiceException.class)
    @Ignore
    public void createPreventsQuestionWithNoIdentifier() {
        Survey survey = createTestSurvey();
        
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setSurveyGuid("AAA");
        survey.getQuestions().add(question);
        
        surveyDao.createSurvey(survey);
    }
    
    @Test(expected=BridgeServiceException.class)
    @Ignore
    public void createPreventsQuestionWithNoSurveyGuid() {
        Survey survey = createTestSurvey();
        
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("AAA");
        survey.getQuestions().add(question);
        
        surveyDao.createSurvey(survey);
    }
    
    @Test(expected=BridgeServiceException.class)
    @Ignore
    public void createPreventsRecreatingASurvey() {
        Survey survey = createTestSurvey();
        
        survey = surveyDao.createSurvey(survey);
        surveyDao.createSurvey(survey);
    }
    
    @Test
    @Ignore
    public void crudSurvey() {
        Survey survey = createTestSurvey();
        
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("age");
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
        survey = surveyDao.getSurvey(survey.getStudyKey(), survey.getGuid(), survey.getVersionedOn());
        assertEquals("Identifier has been changed", "newIdentifier", survey.getIdentifier());
        
        surveyDao.deleteSurvey(survey);
        
        try {
            survey = surveyDao.getSurvey(survey.getStudyKey(), survey.getGuid(), survey.getVersionedOn());
            fail("Should have thrown an exception");
        } catch(SurveyNotFoundException snfe) {
            assertEquals("Correct study key", survey.getStudyKey(), snfe.getStudyKey());
            assertEquals("Correct survey GUID", survey.getGuid(), snfe.getSurveyGuid());
            assertEquals("Correct versionedOn value", survey.getVersionedOn(), snfe.getVersionedOn());
        }
    }

    @Test(expected=SurveyNotFoundException.class)
    @Ignore
    public void failToFindSurveysByBadStudyKey() {
        surveyDao.getSurveys("foo");
    }
    
    @Test
    public void canGetAllSurveys() {
        String studyKey = TestConstants.SECOND_STUDY.getKey();
        
        Survey survey = createTestSurvey("Test Survey 1");
        surveyDao.createSurvey(survey);
        
        survey = createTestSurvey("Test Survey 2");
        surveyDao.createSurvey(survey);
        
        survey = createTestSurvey("Test Survey 3");
        surveyDao.createSurvey(survey);
        
        survey = createTestSurvey("Test Survey 4");
        surveyDao.createSurvey(survey);
        
        survey = createTestSurvey("Test Survey 5");
        surveyDao.createSurvey(survey);
        
        surveyDao.versionSurvey(survey);
        
        List<Survey> surveys = surveyDao.getSurveys(studyKey);
        
        for(Survey s : surveys) {
            System.out.println(s.getName());
        }
        
        assertEquals("All surveys are returned", 6, surveys.size());
        
        surveys = surveyDao.getSurveys(studyKey, survey.getGuid());
        assertEquals("All surveys are returned", 2, surveys.size());
    }
    
    @Test
    @Ignore
    public void canGetAllSurveyVersions() {
        
    }
    
    @Test
    @Ignore
    public void canPublishASurvey() {
        Survey survey = createTestSurvey();
        survey = surveyDao.createSurvey(survey);
        survey = surveyDao.publishSurvey(survey);
        
        assertTrue("Survey is marked published", survey.isPublished());
        
        Survey pubSurvey = surveyDao.getPublishedSurvey(survey.getStudyKey(), survey.getGuid());
        
        assertEquals("Same survey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same survey versionedOn", survey.getVersionedOn(), pubSurvey.getVersionedOn());
        assertTrue("Published survey is marked published", pubSurvey.isPublished());
        
        // Publishing again is harmless
        survey = surveyDao.publishSurvey(survey);
        pubSurvey = surveyDao.getPublishedSurvey(survey.getStudyKey(), survey.getGuid());
        assertEquals("Same survey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same survey versionedOn", survey.getVersionedOn(), pubSurvey.getVersionedOn());
        assertTrue("Published survey is marked published", pubSurvey.isPublished());
    }
    
    @Test(expected=PublishedSurveyException.class)
    @Ignore
    public void cannotUpdatePublishedSurveys() {
        Survey survey = createTestSurvey();
        survey = surveyDao.createSurvey(survey);
        surveyDao.publishSurvey(survey);
        
        survey.setName("This is a new name");
        surveyDao.updateSurvey(survey);
    }
    
    @Test
    @Ignore
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = createTestSurvey();
        survey = surveyDao.createSurvey(survey);
        survey = surveyDao.publishSurvey(survey);
        
        Survey laterSurvey = surveyDao.versionSurvey(survey);
        assertNotEquals("Surveys do not have the same versionedOn", survey.getVersionedOn(), laterSurvey.getVersionedOn());
        
        laterSurvey = surveyDao.publishSurvey(laterSurvey);
        
        Survey pubSurvey = surveyDao.getPublishedSurvey(laterSurvey.getStudyKey(), laterSurvey.getGuid());
        assertEquals("Later survey is the published survey", laterSurvey.getVersionedOn(), pubSurvey.getVersionedOn());
    }
    
    @Test
    @Ignore
    public void crudSurveyQuestions() {
        Survey survey = createTestSurvey();
        
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
        
        survey = surveyDao.getSurvey(survey.getStudyKey(), survey.getGuid(), survey.getVersionedOn());

        assertEquals("Survey only has one question", 1, survey.getQuestions().size());
        assertEquals("Survey has updated the one question's identifier", "new age", survey.getQuestions().get(0)
                .getIdentifier());
    }
    
    @Test
    @Ignore
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = createTestSurvey();
        
        survey = surveyDao.createSurvey(survey);
        surveyDao.publishSurvey(survey);
        
        Long originalVersion = survey.getVersionedOn();
        survey = surveyDao.versionSurvey(survey);
        
        Long newVersion = survey.getVersionedOn();
        assertNotEquals("Versions differ", newVersion, originalVersion);
    }
    
}
