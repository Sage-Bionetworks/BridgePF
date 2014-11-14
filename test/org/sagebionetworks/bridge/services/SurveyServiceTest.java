package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SurveyServiceTest {

    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    SurveyServiceImpl surveyService;

    @Resource
    StudyServiceImpl studyService; 
    
    private TestSurvey testSurvey;
    
    private Study study;
    
    @Before
    public void before() {
        testSurvey = new TestSurvey(true);
        study = studyService.getStudyByIdentifier(TEST_STUDY_IDENTIFIER);
        DynamoInitializer.init(DynamoSurvey.class, DynamoSurveyQuestion.class);
        DynamoTestUtil.clearTable(DynamoSurvey.class);
        DynamoTestUtil.clearTable(DynamoSurveyQuestion.class);
    }
    
    @After
    public void after() {
    }
    
    @Test(expected = InvalidEntityException.class)
    public void createPreventsEmptyStudyKey() {
        testSurvey.setStudyKey(null);
        surveyService.createSurvey(testSurvey);
    }

    @Test(expected = BridgeServiceException.class)
    public void createPreventsNoIdentifier() {
        testSurvey.setIdentifier(null);
        surveyService.createSurvey(testSurvey);
    }

    @Test(expected = BridgeServiceException.class)
    public void createPreventsQuestionWithNoIdentifier() {
        TestSurvey.selectBy(testSurvey, DataType.STRING).setIdentifier(null);
        surveyService.createSurvey(testSurvey);
    }

    @Test(expected = BridgeServiceException.class)
    public void createPreventsRecreatingASurvey() {
        surveyService.createSurvey(testSurvey);
        surveyService.createSurvey(testSurvey);
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void cannotCreateAnExistingSurvey() {
        surveyService.createSurvey(new TestSurvey(false));
    }
    
    @Test
    public void crudSurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);

        assertTrue("Survey has a guid", survey.getGuid() != null);
        assertTrue("Survey has been versioned", survey.getCreatedOn() != 0L);
        assertTrue("Question #1 has a guid", survey.getQuestions().get(0).getGuid() != null);
        assertTrue("Question #2 has a guid", survey.getQuestions().get(1).getGuid() != null);

        survey.setIdentifier("newIdentifier");
        surveyService.updateSurvey(survey);
        survey = surveyService.getSurvey(survey.getGuid(), survey.getCreatedOn());
        assertEquals("Identifier has been changed", "newIdentifier", survey.getIdentifier());

        surveyService.deleteSurvey(survey.getGuid(), survey.getCreatedOn());

        try {
            survey = surveyService.getSurvey(survey.getGuid(), survey.getCreatedOn());
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException enfe) {
        }
    }

    // UPDATE SURVEY

    @Test
    public void canUpdateASurveyVersion() {
        Survey survey = surveyService.createSurvey(testSurvey);

        Survey nextVersion = surveyService.versionSurvey(survey.getGuid(), survey.getCreatedOn());

        // If you change these, it looks like a different testSurvey, you'll just get a not found exception.
        // testSurvey.setGuid("A");
        // testSurvey.setStudyKey("E");
        // testSurvey.setCreatedOn(new DateTime().getMillis());
        survey.setIdentifier("B");
        survey.setName("C");

        surveyService.updateSurvey(survey);
        survey = surveyService.getSurvey(survey.getGuid(), survey.getCreatedOn());

        assertEquals("Identifier can be updated", "B", survey.getIdentifier());
        assertEquals("Name can be updated", "C", survey.getName());

        // Now verify the nextVersion has not been changed
        nextVersion = surveyService.getSurvey(nextVersion.getGuid(), nextVersion.getCreatedOn());
        assertEquals("Next version has same identifier", "bloodpressure", nextVersion.getIdentifier());
        assertEquals("Next name has not changed", "General Blood Pressure Survey", nextVersion.getName());
    }

    @Test
    public void crudSurveyQuestions() {
        Survey survey = surveyService.createSurvey(testSurvey);

        int count = survey.getQuestions().size();
        
        // Now, alter these, and verify they are altered
        survey.getQuestions().remove(0);
        survey.getQuestions().get(6).setIdentifier("new gender");
        surveyService.updateSurvey(survey);

        survey = surveyService.getSurvey(survey.getGuid(), survey.getCreatedOn());

        assertEquals("Survey has one less question", count-1, survey.getQuestions().size());
        
        SurveyQuestion restored = survey.getQuestions().get(6);
        MultiValueConstraints mvc = (MultiValueConstraints)restored.getConstraints();
        
        assertEquals("Survey has updated the one question's identifier", "new gender", restored.getIdentifier());
        MultiValueConstraints sc = (MultiValueConstraints)restored.getConstraints();
        assertEquals("Constraints have correct enumeration", mvc.getEnumeration(), sc.getEnumeration());
        assertEquals("Question has the correct UIHint", UIHint.LIST, restored.getUiHint());
    }

    @Test(expected = ConcurrentModificationException.class)
    public void cannotUpdateVersionWithoutException() {
        Survey survey = surveyService.createSurvey(testSurvey);
        survey.setVersion(44L);
        
        surveyService.updateSurvey(survey);
    }

    @Test(expected = PublishedSurveyException.class)
    public void cannotUpdatePublishedSurveys() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveyService.publishSurvey(survey.getGuid(), survey.getCreatedOn());

        survey.setName("This is a new name");
        surveyService.updateSurvey(survey);
    }

    // VERSION SURVEY

    @Test
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveyService.publishSurvey(survey.getGuid(), survey.getCreatedOn());

        Long originalVersion = survey.getCreatedOn();
        survey = surveyService.versionSurvey(survey.getGuid(), survey.getCreatedOn());

        assertEquals("Newly versioned testSurvey is not published", false, survey.isPublished());

        Long newVersion = survey.getCreatedOn();
        assertNotEquals("Versions differ", newVersion, originalVersion);
    }

    @Test
    public void versioningASurveyCopiesTheQuestions() {
        Survey survey = surveyService.createSurvey(testSurvey);
        String v1SurveyCompoundKey = survey.getQuestions().get(0).getSurveyCompoundKey();
        String v1Guid = survey.getQuestions().get(0).getGuid();

        survey = surveyService.versionSurvey(survey.getGuid(), survey.getCreatedOn());
        String v2SurveyCompoundKey = survey.getQuestions().get(0).getSurveyCompoundKey();
        String v2Guid = survey.getQuestions().get(0).getGuid();

        assertNotEquals("Survey reference differs", v1SurveyCompoundKey, v2SurveyCompoundKey);
        assertNotEquals("Survey question GUID differs", v1Guid, v2Guid);
    }

    // PUBLISH SURVEY

    @Test
    public void canPublishASurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        survey = surveyService.publishSurvey(survey.getGuid(), survey.getCreatedOn());

        assertTrue("Survey is marked published", survey.isPublished());

        Survey pubSurvey = surveyService.getMostRecentlyPublishedSurveys(study).get(0);

        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // Publishing again is harmless
        survey = surveyService.publishSurvey(survey.getGuid(), survey.getCreatedOn());
        pubSurvey = surveyService.getMostRecentlyPublishedSurveys(study).get(0);
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());
    }

    @Test
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        survey = surveyService.publishSurvey(survey.getGuid(), survey.getCreatedOn());

        Survey laterSurvey = surveyService.versionSurvey(survey.getGuid(), survey.getCreatedOn());
        assertNotEquals("Surveys do not have the same createdOn", survey.getCreatedOn(),
                laterSurvey.getCreatedOn());

        laterSurvey = surveyService.publishSurvey(laterSurvey.getGuid(), laterSurvey.getCreatedOn());

        Survey pubSurvey = surveyService.getMostRecentlyPublishedSurveys(study).get(0);
        assertEquals("Later testSurvey is the published testSurvey", laterSurvey.getCreatedOn(), pubSurvey.getCreatedOn());
    }

    // GET SURVEYS

    @Test
    public void failToGetSurveysByBadStudyKey() {
        List<Survey> surveys = surveyService.getSurveys(new Study("foo", "foo", 17, "", null, null, "foo_researcher"));
        assertEquals("No surveys", 0, surveys.size());
    }

    @Test
    public void canGetAllSurveys() {
        surveyService.createSurvey(new TestSurvey(true));
        surveyService.createSurvey(new TestSurvey(true));
        surveyService.createSurvey(new TestSurvey(true));
        surveyService.createSurvey(new TestSurvey(true));

        Survey survey = surveyService.createSurvey(new TestSurvey(true));

        surveyService.versionSurvey(survey.getGuid(), survey.getCreatedOn());

        // Get all surveys
        List<Survey> surveys = surveyService.getSurveys(study);

        assertEquals("All surveys are returned", 6, surveys.size());

        // Get all surveys of a version
        surveys = surveyService.getAllVersionsOfSurvey(survey.getGuid());
        assertEquals("All surveys are returned", 2, surveys.size());

        Survey survey1 = surveys.get(0);
        Survey survey2 = surveys.get(1);
        assertEquals("Surveys have same GUID", survey1.getGuid(), survey2.getGuid());
        assertEquals("Surveys have same Study key", survey1.getStudyKey(), survey2.getStudyKey());
        assertNotEquals("Surveys have different createdOn attribute", survey1.getCreatedOn(),
                survey2.getCreatedOn());
    }

    // CLOSE SURVEY

    @Test
    public void canClosePublishedSurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        survey = surveyService.publishSurvey(survey.getGuid(), survey.getCreatedOn());

        survey = surveyService.closeSurvey(survey.getGuid(), survey.getCreatedOn());
        assertEquals("Survey no longer published", false, survey.isPublished());

        survey = surveyService.getSurvey(survey.getGuid(), survey.getCreatedOn());
        assertEquals("Survey no longer published", false, survey.isPublished());
    }

    // GET PUBLISHED SURVEY

    @Test
    public void canRetrieveMostRecentlyPublishedSurveysWithManyVersions() {
        // Version 1.
        Survey survey1 = surveyService.createSurvey(new TestSurvey(true));

        // Version 2.
        Survey survey2 = surveyService.versionSurvey(survey1.getGuid(), survey1.getCreatedOn());

        // Version 3 (tossed)
        surveyService.versionSurvey(survey2.getGuid(), survey2.getCreatedOn());

        // Publish one version
        surveyService.publishSurvey(survey1.getGuid(), survey1.getCreatedOn());

        List<Survey> surveys = surveyService.getMostRecentlyPublishedSurveys(study);
        assertEquals("Retrieved published testSurvey v1", survey1.getCreatedOn(), surveys.get(0).getCreatedOn());

        // Publish a later version
        surveyService.publishSurvey(survey2.getGuid(), survey2.getCreatedOn());

        // Now the most recent version of this testSurvey should be survey2.
        surveys = surveyService.getMostRecentlyPublishedSurveys(study);
        assertEquals("Retrieved published testSurvey v2", survey2.getCreatedOn(), surveys.get(0).getCreatedOn());
    }

    @Test
    public void canRetrieveMostRecentPublishedSurveysWithManySurveys() {
        Survey survey1 = surveyService.createSurvey(new TestSurvey(true));
        surveyService.publishSurvey(survey1.getGuid(), survey1.getCreatedOn());

        Survey survey2 = surveyService.createSurvey(new TestSurvey(true));
        surveyService.publishSurvey(survey2.getGuid(), survey2.getCreatedOn());

        Survey survey3 = surveyService.createSurvey(new TestSurvey(true));
        surveyService.publishSurvey(survey3.getGuid(), survey3.getCreatedOn());

        List<Survey> published = surveyService.getMostRecentlyPublishedSurveys(study);

        assertEquals("There are three published surveys", 3, published.size());
        assertEquals("The first is survey3", survey3.getGuid(), published.get(0).getGuid());
        assertEquals("The middle is survey2", survey2.getGuid(), published.get(1).getGuid());
        assertEquals("The last is survey1", survey1.getGuid(), published.get(2).getGuid());
    }

    // DELETE SURVEY

    @Test(expected = PublishedSurveyException.class)
    public void cannotDeleteAPublishedSurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveyService.publishSurvey(survey.getGuid(), survey.getCreatedOn());

        surveyService.deleteSurvey(survey.getGuid(), survey.getCreatedOn());
    }
    
}
