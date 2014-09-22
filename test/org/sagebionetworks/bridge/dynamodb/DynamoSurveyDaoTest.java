package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.PublishedSurveyException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSurveyDaoTest {

    @Resource
    DynamoSurveyDao surveyDao;

    private static final String STUDY_KEY = TestConstants.SECOND_STUDY.getKey();
    private TestSurvey testSurvey;
    
    @Before
    public void before() {
        testSurvey = new TestSurvey(true);
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        DynamoTestUtil.clearTable(DynamoSurvey.class, "studyKey", "modifiedOn", "identifier", "name", "published",
                "version");
        DynamoTestUtil.clearTable(DynamoSurveyQuestion.class, "guid", "identifier", "data");
        List<Survey> surveys = surveyDao.getSurveys(STUDY_KEY);
        for (Survey survey : surveys) {
            surveyDao.closeSurvey(survey.getGuid(), survey.getVersionedOn());
            surveyDao.deleteSurvey(survey.getGuid(), survey.getVersionedOn());
        }
    }

    // CREATE SURVEY

    @Test(expected = BridgeServiceException.class)
    public void createPreventsEmptyStudyKey() {
        testSurvey.setStudyKey(null);
        surveyDao.createSurvey(testSurvey);
    }

    @Test(expected = BridgeServiceException.class)
    public void createPreventsNoIdentifier() {
        testSurvey.setIdentifier(null);
        surveyDao.createSurvey(testSurvey);
    }

    @Test(expected = BridgeServiceException.class)
    public void createPreventsQuestionWithNoIdentifier() {
        testSurvey.getStringQuestion().setIdentifier(null);
        surveyDao.createSurvey(testSurvey);
    }

    @Test(expected = BridgeServiceException.class)
    public void createPreventsRecreatingASurvey() {
        surveyDao.createSurvey(testSurvey);
        surveyDao.createSurvey(testSurvey);
    }

    @Test
    public void crudSurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);

        assertTrue("Survey has a guid", survey.getGuid() != null);
        assertTrue("Survey has been versioned", survey.getVersionedOn() != 0L);
        assertTrue("Question #1 has a guid", survey.getQuestions().get(0).getGuid() != null);
        assertTrue("Question #2 has a guid", survey.getQuestions().get(1).getGuid() != null);

        survey.setIdentifier("newIdentifier");
        surveyDao.updateSurvey(survey);
        survey = surveyDao.getSurvey(survey.getGuid(), survey.getVersionedOn());
        assertEquals("Identifier has been changed", "newIdentifier", survey.getIdentifier());

        surveyDao.deleteSurvey(survey.getGuid(), survey.getVersionedOn());

        try {
            survey = surveyDao.getSurvey(survey.getGuid(), survey.getVersionedOn());
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException enfe) {
        }
    }

    // UPDATE SURVEY

    @Test
    public void canUpdateASurveyVersion() {
        Survey survey = surveyDao.createSurvey(testSurvey);

        Survey nextVersion = surveyDao.versionSurvey(survey.getGuid(), survey.getVersionedOn());

        // If you change these, it looks like a different testSurvey, you'll just get a not found exception.
        // testSurvey.setGuid("A");
        // testSurvey.setStudyKey("E");
        // testSurvey.setVersionedOn(new DateTime().getMillis());
        survey.setIdentifier("B");
        survey.setName("C");

        surveyDao.updateSurvey(survey);
        survey = surveyDao.getSurvey(survey.getGuid(), survey.getVersionedOn());

        assertEquals("Identifier can be updated", "B", survey.getIdentifier());
        assertEquals("Name can be updated", "C", survey.getName());

        // Now verify the nextVersion has not been changed
        nextVersion = surveyDao.getSurvey(nextVersion.getGuid(), nextVersion.getVersionedOn());
        assertEquals("Next version has same identifier", "bloodpressure", nextVersion.getIdentifier());
        assertEquals("Next name has not changed", "General Blood Pressure Survey", nextVersion.getName());
    }

    @Test
    public void crudSurveyQuestions() {
        Survey survey = surveyDao.createSurvey(testSurvey);

        int count = survey.getQuestions().size();
        
        // Now, alter these, and verify they are altered
        survey.getQuestions().remove(0);
        survey.getQuestions().get(6).setIdentifier("new gender");
        surveyDao.updateSurvey(survey);

        survey = surveyDao.getSurvey(survey.getGuid(), survey.getVersionedOn());

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
        Survey survey = surveyDao.createSurvey(testSurvey);
        survey.setVersion(44L);
        surveyDao.updateSurvey(survey);
    }

    @Test(expected = PublishedSurveyException.class)
    public void cannotUpdatePublishedSurveys() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveyDao.publishSurvey(survey.getGuid(), survey.getVersionedOn());

        survey.setName("This is a new name");
        surveyDao.updateSurvey(survey);
    }

    // VERSION SURVEY

    @Test
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveyDao.publishSurvey(survey.getGuid(), survey.getVersionedOn());

        Long originalVersion = survey.getVersionedOn();
        survey = surveyDao.versionSurvey(survey.getGuid(), survey.getVersionedOn());

        assertEquals("Newly versioned testSurvey is not published", false, survey.isPublished());

        Long newVersion = survey.getVersionedOn();
        assertNotEquals("Versions differ", newVersion, originalVersion);
    }

    @Test
    public void versioningASurveyCopiesTheQuestions() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        String v1SurveyCompoundKey = survey.getQuestions().get(0).getSurveyCompoundKey();
        String v1Guid = survey.getQuestions().get(0).getGuid();

        survey = surveyDao.versionSurvey(survey.getGuid(), survey.getVersionedOn());
        String v2SurveyCompoundKey = survey.getQuestions().get(0).getSurveyCompoundKey();
        String v2Guid = survey.getQuestions().get(0).getGuid();

        assertNotEquals("Survey reference differs", v1SurveyCompoundKey, v2SurveyCompoundKey);
        assertNotEquals("Survey question GUID differs", v1Guid, v2Guid);
    }

    // PUBLISH SURVEY

    @Test
    public void canPublishASurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        survey = surveyDao.publishSurvey(survey.getGuid(), survey.getVersionedOn());

        assertTrue("Survey is marked published", survey.isPublished());

        Survey pubSurvey = surveyDao.getMostRecentlyPublishedSurveys(STUDY_KEY).get(0);

        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey versionedOn", survey.getVersionedOn(), pubSurvey.getVersionedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // Publishing again is harmless
        survey = surveyDao.publishSurvey(survey.getGuid(), survey.getVersionedOn());
        pubSurvey = surveyDao.getMostRecentlyPublishedSurveys(STUDY_KEY).get(0);
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey versionedOn", survey.getVersionedOn(), pubSurvey.getVersionedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());
    }

    @Test
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        survey = surveyDao.publishSurvey(survey.getGuid(), survey.getVersionedOn());

        Survey laterSurvey = surveyDao.versionSurvey(survey.getGuid(), survey.getVersionedOn());
        assertNotEquals("Surveys do not have the same versionedOn", survey.getVersionedOn(),
                laterSurvey.getVersionedOn());

        laterSurvey = surveyDao.publishSurvey(laterSurvey.getGuid(), laterSurvey.getVersionedOn());

        Survey pubSurvey = surveyDao.getMostRecentlyPublishedSurveys(STUDY_KEY).get(0);
        assertEquals("Later testSurvey is the published testSurvey", laterSurvey.getVersionedOn(), pubSurvey.getVersionedOn());
    }

    // GET SURVEYS

    @Test
    public void failToGetSurveysByBadStudyKey() {
        List<Survey> surveys = surveyDao.getSurveys("foo");
        assertEquals("No surveys", 0, surveys.size());
    }

    @Test
    public void canGetAllSurveys() {
        surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.createSurvey(new TestSurvey(true));

        Survey survey = surveyDao.createSurvey(new TestSurvey(true));

        surveyDao.versionSurvey(survey.getGuid(), survey.getVersionedOn());

        // Get all surveys
        List<Survey> surveys = surveyDao.getSurveys(STUDY_KEY);

        assertEquals("All surveys are returned", 6, surveys.size());

        // Get all surveys of a version
        surveys = surveyDao.getSurveyVersions(survey.getGuid());
        assertEquals("All surveys are returned", 2, surveys.size());

        Survey survey1 = surveys.get(0);
        Survey survey2 = surveys.get(1);
        assertEquals("Surveys have same GUID", survey1.getGuid(), survey2.getGuid());
        assertEquals("Surveys have same Study key", survey1.getStudyKey(), survey2.getStudyKey());
        assertNotEquals("Surveys have different versionedOn attribute", survey1.getVersionedOn(),
                survey2.getVersionedOn());
    }

    // CLOSE SURVEY

    @Test
    public void canClosePublishedSurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        survey = surveyDao.publishSurvey(survey.getGuid(), survey.getVersionedOn());

        survey = surveyDao.closeSurvey(survey.getGuid(), survey.getVersionedOn());
        assertEquals("Survey no longer published", false, survey.isPublished());

        survey = surveyDao.getSurvey(survey.getGuid(), survey.getVersionedOn());
        assertEquals("Survey no longer published", false, survey.isPublished());
    }

    // GET PUBLISHED SURVEY

    @Test
    public void canRetrieveMostRecentlyPublishedSurveysWithManyVersions() {
        // Version 1.
        Survey survey1 = surveyDao.createSurvey(new TestSurvey(true));

        // Version 2.
        Survey survey2 = surveyDao.versionSurvey(survey1.getGuid(), survey1.getVersionedOn());

        // Version 3 (tossed)
        surveyDao.versionSurvey(survey2.getGuid(), survey2.getVersionedOn());

        // Publish one version
        surveyDao.publishSurvey(survey1.getGuid(), survey1.getVersionedOn());

        List<Survey> surveys = surveyDao.getMostRecentlyPublishedSurveys(STUDY_KEY);
        assertEquals("Retrieved published testSurvey v1", survey1.getVersionedOn(), surveys.get(0).getVersionedOn());

        // Publish a later version
        surveyDao.publishSurvey(survey2.getGuid(), survey2.getVersionedOn());

        // Now the most recent version of this testSurvey should be survey2.
        surveys = surveyDao.getMostRecentlyPublishedSurveys(STUDY_KEY);
        assertEquals("Retrieved published testSurvey v2", survey2.getVersionedOn(), surveys.get(0).getVersionedOn());
    }

    @Test
    public void canRetrieveMostRecentPublishedSurveysWithManySurveys() {
        Survey survey1 = surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.publishSurvey(survey1.getGuid(), survey1.getVersionedOn());

        Survey survey2 = surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.publishSurvey(survey2.getGuid(), survey2.getVersionedOn());

        Survey survey3 = surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.publishSurvey(survey3.getGuid(), survey3.getVersionedOn());

        List<Survey> published = surveyDao.getMostRecentlyPublishedSurveys(STUDY_KEY);

        assertEquals("There are three published surveys", 3, published.size());
        assertEquals("The first is survey3", survey3.getGuid(), published.get(0).getGuid());
        assertEquals("The middle is survey2", survey2.getGuid(), published.get(1).getGuid());
        assertEquals("The last is survey1", survey1.getGuid(), published.get(2).getGuid());
    }

    // DELETE SURVEY

    @Test(expected = PublishedSurveyException.class)
    public void cannotDeleteAPublishedSurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveyDao.publishSurvey(survey.getGuid(), survey.getVersionedOn());

        surveyDao.deleteSurvey(survey.getGuid(), survey.getVersionedOn());
    }

    // GET SURVEY
    // * Covered by other tests

}
