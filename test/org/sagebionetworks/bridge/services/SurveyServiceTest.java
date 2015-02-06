package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
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
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyElement;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.Image;
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
        study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        DynamoInitializer.init(DynamoSurvey.class, DynamoSurveyElement.class);
        DynamoTestUtil.clearTable(DynamoSurvey.class);
        DynamoTestUtil.clearTable(DynamoSurveyElement.class);
    }
    
    @After
    public void after() {
    }
    
    @Test(expected = InvalidEntityException.class)
    public void createPreventsEmptyStudyKey() {
        testSurvey.setStudyIdentifier(null);
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
        assertTrue("Question #1 has a guid", survey.getElements().get(0).getGuid() != null);
        assertTrue("Question #2 has a guid", survey.getElements().get(1).getGuid() != null);

        survey.setIdentifier("newIdentifier");
        surveyService.updateSurvey(survey);
        survey = surveyService.getSurvey(survey);
        assertEquals("Identifier has been changed", "newIdentifier", survey.getIdentifier());

        surveyService.deleteSurvey(study, survey);

        try {
            survey = surveyService.getSurvey(survey);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException enfe) {
        }
    }

    // UPDATE SURVEY

    @Test
    public void canUpdateASurveyVersion() {
        Survey survey = surveyService.createSurvey(testSurvey);

        Survey nextVersion = surveyService.versionSurvey(survey);

        // If you change these, it looks like a different testSurvey, you'll just get a not found exception.
        // testSurvey.setGuid("A");
        // testSurvey.setStudyKey("E");
        // testSurvey.setCreatedOn(new DateTime().getMillis());
        survey.setIdentifier("B");
        survey.setName("C");

        surveyService.updateSurvey(survey);
        survey = surveyService.getSurvey(survey);

        assertEquals("Identifier can be updated", "B", survey.getIdentifier());
        assertEquals("Name can be updated", "C", survey.getName());

        // Now verify the nextVersion has not been changed
        nextVersion = surveyService.getSurvey(nextVersion);
        assertEquals("Next version has same identifier", "bloodpressure", nextVersion.getIdentifier());
        assertEquals("Next name has not changed", "General Blood Pressure Survey", nextVersion.getName());
    }

    @Test
    public void crudSurveyQuestions() {
        Survey survey = surveyService.createSurvey(testSurvey);

        int count = survey.getElements().size();
        
        // Now, alter these, and verify they are altered
        survey.getElements().remove(0);
        survey.getElements().get(6).setIdentifier("new gender");
        surveyService.updateSurvey(survey);

        survey = surveyService.getSurvey(survey);

        assertEquals("Survey has one less question", count-1, survey.getElements().size());
        
        SurveyQuestion restored = (SurveyQuestion)survey.getElements().get(6);
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
        surveyService.publishSurvey(survey);

        survey.setName("This is a new name");
        surveyService.updateSurvey(survey);
    }

    // VERSION SURVEY

    @Test
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveyService.publishSurvey(survey);

        Long originalVersion = survey.getCreatedOn();
        survey = surveyService.versionSurvey(survey);

        assertEquals("Newly versioned testSurvey is not published", false, survey.isPublished());

        Long newVersion = survey.getCreatedOn();
        assertNotEquals("Versions differ", newVersion, originalVersion);
    }

    @Test
    public void versioningASurveyCopiesTheQuestions() {
        Survey survey = surveyService.createSurvey(testSurvey);
        String v1SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v1Guid = survey.getElements().get(0).getGuid();

        survey = surveyService.versionSurvey(survey);
        String v2SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v2Guid = survey.getElements().get(0).getGuid();

        assertNotEquals("Survey reference differs", v1SurveyCompoundKey, v2SurveyCompoundKey);
        assertNotEquals("Survey question GUID differs", v1Guid, v2Guid);
    }

    // PUBLISH SURVEY

    @Test
    public void canPublishASurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        survey = surveyService.publishSurvey(survey);

        assertTrue("Survey is marked published", survey.isPublished());

        Survey pubSurvey = surveyService.getAllSurveysMostRecentlyPublishedVersion(study).get(0);

        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // Publishing again is harmless
        survey = surveyService.publishSurvey(survey);
        pubSurvey = surveyService.getAllSurveysMostRecentlyPublishedVersion(study).get(0);
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());
    }

    @Test
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        survey = surveyService.publishSurvey(survey);

        Survey laterSurvey = surveyService.versionSurvey(survey);
        assertNotEquals("Surveys do not have the same createdOn", survey.getCreatedOn(),
                laterSurvey.getCreatedOn());

        laterSurvey = surveyService.publishSurvey(laterSurvey);

        Survey pubSurvey = surveyService.getAllSurveysMostRecentlyPublishedVersion(study).get(0);
        assertEquals("Later testSurvey is the published testSurvey", laterSurvey.getCreatedOn(), pubSurvey.getCreatedOn());
    }

    // GET SURVEYS

    @Test
    public void failToGetSurveysByBadStudyKey() {
        Study study = new DynamoStudy();
        study.setName("foo");
        study.setIdentifier("foo");
        study.setMinAgeOfConsent(17);
        study.setResearcherRole("foo_researcher");
        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(study);
        assertEquals("No surveys", 0, surveys.size());
    }

    @Test
    public void canGetAllSurveys() {
        surveyService.createSurvey(new TestSurvey(true));
        surveyService.createSurvey(new TestSurvey(true));
        surveyService.createSurvey(new TestSurvey(true));
        surveyService.createSurvey(new TestSurvey(true));

        Survey survey = surveyService.createSurvey(new TestSurvey(true));

        surveyService.versionSurvey(survey);

        // Get all surveys
        
        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(study);
        assertEquals("All surveys are returned", 5, surveys.size());

        // Get all surveys of a version
        surveys = surveyService.getSurveyAllVersions(study, survey.getGuid());
        assertEquals("All surveys are returned", 2, surveys.size());

        Survey version1 = surveys.get(0);
        Survey version2 = surveys.get(1);
        assertEquals("Surveys have same GUID", version1.getGuid(), version2.getGuid());
        assertEquals("Surveys have same Study key", version1.getStudyIdentifier(), version2.getStudyIdentifier());
        assertNotEquals("Surveys have different createdOn attribute", version1.getCreatedOn(),
                version2.getCreatedOn());
    }

    // CLOSE SURVEY

    @Test
    public void canClosePublishedSurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        survey = surveyService.publishSurvey(survey);

        survey = surveyService.closeSurvey(survey);
        assertEquals("Survey no longer published", false, survey.isPublished());

        survey = surveyService.getSurvey(survey);
        assertEquals("Survey no longer published", false, survey.isPublished());
    }

    // GET PUBLISHED SURVEY

    @Test
    public void canRetrieveMostRecentlyPublishedSurveysWithManyVersions() {
        // Version 1.
        Survey survey1 = surveyService.createSurvey(new TestSurvey(true));

        // Version 2.
        Survey survey2 = surveyService.versionSurvey(survey1);

        // Version 3 (tossed)
        surveyService.versionSurvey(survey2);

        // Publish one version
        surveyService.publishSurvey(survey1);

        List<Survey> surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(study);
        assertEquals("Retrieved published testSurvey v1", survey1.getCreatedOn(), surveys.get(0).getCreatedOn());

        // Publish a later version
        surveyService.publishSurvey(survey2);

        // Now the most recent version of this testSurvey should be survey2.
        surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(study);
        assertEquals("Retrieved published testSurvey v2", survey2.getCreatedOn(), surveys.get(0).getCreatedOn());
    }

    @Test
    public void canRetrieveMostRecentPublishedSurveysWithManySurveys() {
        Survey survey1 = surveyService.createSurvey(new TestSurvey(true));
        surveyService.publishSurvey(survey1);

        Survey survey2 = surveyService.createSurvey(new TestSurvey(true));
        surveyService.publishSurvey(survey2);

        Survey survey3 = surveyService.createSurvey(new TestSurvey(true));
        surveyService.publishSurvey(survey3);

        List<Survey> published = surveyService.getAllSurveysMostRecentlyPublishedVersion(study);

        assertEquals("There are three published surveys", 3, published.size());
        assertEquals("The first is survey3", survey3.getGuid(), published.get(0).getGuid());
        assertEquals("The middle is survey2", survey2.getGuid(), published.get(1).getGuid());
        assertEquals("The last is survey1", survey1.getGuid(), published.get(2).getGuid());
    }

    // DELETE SURVEY

    @Test(expected = PublishedSurveyException.class)
    public void cannotDeleteAPublishedSurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveyService.publishSurvey(survey);

        surveyService.deleteSurvey(study, survey);
    }
    
    @Test
    public void canRetrieveASurveyByIdentifier() {
        String identifier = TestUtils.randomName();
        Survey survey = new TestSurvey(true);
        survey.setName("This is a different test name");
        survey.setIdentifier(identifier);
        
        GuidCreatedOnVersionHolder keys = surveyService.createSurvey(survey);
        surveyService.publishSurvey(keys);
        
        Survey found = surveyService.getSurveyMostRecentlyPublishedVersionByIdentifier(study, identifier);
        assertNotNull(found);
        assertEquals(survey.getName(), found.getName());
    }
    
    @Test
    public void validationInfoScreen() {
        Survey survey = new TestSurvey(true);
        
        DynamoSurveyInfoScreen screen = new DynamoSurveyInfoScreen();
        screen.setImage(new Image("/path/to/source.gif", 0, 0)); // very wrong
        survey.getElements().add(0, screen);
        
        try {
            surveyService.createSurvey(survey);
            fail("Service should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("Survey is invalid: element0.identifier is required; element0.title is required; element0.prompt is required; element0.image.source must be a valid URL to an image; element0.image.width is required; element0.image.height is required", e.getMessage());
        }
    }

}
