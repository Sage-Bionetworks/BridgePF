package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

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
        DynamoTestUtil.clearTable(DynamoSurvey.class, "studyKey", "guid", "versionedOn", "version", "name",
                "identifier", "ownerGroup", "published");
        DynamoTestUtil.clearTable(DynamoSurveyQuestion.class, "surveyGuid", "guid", "order", "identifier", "data");
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoSurvey.class, "studyKey", "guid", "versionedOn", "version", "name",
                "identifier", "ownerGroup", "published");
        DynamoTestUtil.clearTable(DynamoSurveyQuestion.class, "surveyGuid", "guid", "order", "identifier", "data");
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
        survey.getQuestions().add(question);
        
        question = new DynamoSurveyQuestion();
        question.setIdentifier("gender");
        survey.getQuestions().add(question);
        
        survey = surveyDao.createSurvey(survey);
        
        assertTrue("Survey has a guid", survey.getGuid() != null);
        assertTrue("Survey has been versioned", survey.getVersionedOn() != 0L);
        assertTrue("Question #1 has a guid", survey.getQuestions().get(0).getGuid() != null);
        assertTrue("Question #2 has a guid", survey.getQuestions().get(0).getGuid() != null);
        
        surveyDao.deleteSurvey(survey.getGuid());
    }

}
