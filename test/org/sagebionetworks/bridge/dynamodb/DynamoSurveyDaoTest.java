package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.UUID;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSurveyDaoTest {

    @Resource
    DynamoSurveyDao surveyDao;
    
    @Resource
    DynamoSurveyResponseDao surveyResponseDao;
    
    @Resource
    DynamoSchedulePlanDao schedulePlanDao;

    private TestSurvey testSurvey;
    
    private StudyIdentifier studyIdentifier;
    
    @Before
    public void before() {
        testSurvey = new TestSurvey(true);
        studyIdentifier = new StudyIdentifierImpl(TEST_STUDY_IDENTIFIER);
        DynamoInitializer.init(DynamoSurvey.class, DynamoSurveyElement.class);
        DynamoTestUtil.clearTable(DynamoSurvey.class);
        DynamoTestUtil.clearTable(DynamoSurveyElement.class);
    }

    // CREATE SURVEY

    // Not an ideal test, but this is thrown from a precondition, nothing changes
    @Test(expected = NullPointerException.class)
    public void createPreventsEmptyStudyKey() {
        testSurvey.setStudyIdentifier(null);
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
        assertTrue("Survey has been versioned", survey.getCreatedOn() != 0L);
        assertTrue("Question #1 has a guid", survey.getElements().get(0).getGuid() != null);
        assertTrue("Question #2 has a guid", survey.getElements().get(1).getGuid() != null);

        survey.setIdentifier("newIdentifier");
        surveyDao.updateSurvey(survey);
        survey = surveyDao.getSurvey(survey);
        assertEquals("Identifier has been changed", "newIdentifier", survey.getIdentifier());

        surveyDao.deleteSurvey(studyIdentifier, survey);

        try {
            survey = surveyDao.getSurvey(survey);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException enfe) {
        }
    }

    // UPDATE SURVEY

    @Test
    public void canUpdateASurveyVersion() {
        Survey survey = surveyDao.createSurvey(testSurvey);

        Survey nextVersion = surveyDao.versionSurvey(survey);

        // If you change these, it looks like a different testSurvey, you'll just get a not found exception.
        // testSurvey.setGuid("A");
        // testSurvey.setStudyKey("E");
        // testSurvey.setCreatedOn(new DateTime().getMillis());
        survey.setIdentifier("B");
        survey.setName("C");

        surveyDao.updateSurvey(survey);
        survey = surveyDao.getSurvey(survey);

        assertEquals("Identifier can be updated", "B", survey.getIdentifier());
        assertEquals("Name can be updated", "C", survey.getName());

        // Now verify the nextVersion has not been changed
        nextVersion = surveyDao.getSurvey(nextVersion);
        assertEquals("Next version has same identifier", "bloodpressure", nextVersion.getIdentifier());
        assertEquals("Next name has not changed", "General Blood Pressure Survey", nextVersion.getName());
    }

    @Test
    public void crudSurveyQuestions() {
        Survey survey = surveyDao.createSurvey(testSurvey);

        int count = survey.getElements().size();
        
        // Now, alter these, and verify they are altered
        survey.getElements().remove(0);
        survey.getElements().get(6).setIdentifier("new gender");
        surveyDao.updateSurvey(survey);

        survey = surveyDao.getSurvey(survey);

        assertEquals("Survey has one less question", count-1, survey.getElements().size());
        
        // TODO: So this doesn't work, from a concrete parent class to sub-interface.
        // BUT: getting closer.
        SurveyQuestion restored = (SurveyQuestion)survey.getElements().get(6);
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
        surveyDao.publishSurvey(survey);

        survey.setName("This is a new name");
        surveyDao.updateSurvey(survey);
    }

    // VERSION SURVEY

    @Test
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveyDao.publishSurvey(survey);

        Long originalVersion = survey.getCreatedOn();
        survey = surveyDao.versionSurvey(survey);

        assertEquals("Newly versioned testSurvey is not published", false, survey.isPublished());

        Long newVersion = survey.getCreatedOn();
        assertNotEquals("Versions differ", newVersion, originalVersion);
    }

    @Test
    public void versioningASurveyCopiesTheQuestions() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        String v1SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v1Guid = survey.getElements().get(0).getGuid();

        survey = surveyDao.versionSurvey(survey);
        String v2SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v2Guid = survey.getElements().get(0).getGuid();

        assertNotEquals("Survey reference differs", v1SurveyCompoundKey, v2SurveyCompoundKey);
        assertNotEquals("Survey question GUID differs", v1Guid, v2Guid);
    }

    // PUBLISH SURVEY

    @Test
    public void canPublishASurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        survey = surveyDao.publishSurvey(survey);

        assertTrue("Survey is marked published", survey.isPublished());

        Survey pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, survey.getGuid());

        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // Publishing again is harmless
        survey = surveyDao.publishSurvey(survey);
        pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, survey.getGuid());
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());
    }

    @Test
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        survey = surveyDao.publishSurvey(survey);

        Survey laterSurvey = surveyDao.versionSurvey(survey);
        assertNotEquals("Surveys do not have the same createdOn", survey.getCreatedOn(),
                laterSurvey.getCreatedOn());

        laterSurvey = surveyDao.publishSurvey(laterSurvey);

        Survey pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, survey.getGuid());
        
        assertEquals("Later testSurvey is the published testSurvey", laterSurvey.getCreatedOn(), pubSurvey.getCreatedOn());
    }

    // GET SURVEYS

    private class SimpleSurvey extends DynamoSurvey {
        public SimpleSurvey() {
            setName("General Blood Pressure Survey");
            setIdentifier("bloodpressure");
            setStudyIdentifier(studyIdentifier.getIdentifier());
        }
    }
    
    @Test
    public void failToGetSurveysByBadStudyKey() {
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentVersion(new StudyIdentifierImpl("foo"));
        assertEquals("No surveys", 0, surveys.size());
    }

    @Test
    public void getSurveyAllVersions() {
        // Get a survey (one GUID), and no other surveys, all the versions, ordered most to least recent
        surveyDao.createSurvey(new SimpleSurvey()); // spurious survey
        
        Survey versionedSurvey = surveyDao.createSurvey(new SimpleSurvey());
        surveyDao.versionSurvey(versionedSurvey);
        surveyDao.versionSurvey(versionedSurvey);
        long lastCreatedOnTime = surveyDao.versionSurvey(versionedSurvey).getCreatedOn();
        
        List<Survey> surveyVersions = surveyDao.getSurveyAllVersions(studyIdentifier, versionedSurvey.getGuid());
        
        for (Survey survey : surveyVersions) {
            assertEquals("All surveys verions of one survey", versionedSurvey.getGuid(), survey.getGuid());
        }
        assertEquals("First survey is the most recently versioned", lastCreatedOnTime, surveyVersions.get(0).getCreatedOn());
        assertNotEquals("createdOn updated", lastCreatedOnTime, versionedSurvey.getCreatedOn());
    }
    
    @Test
    public void getSurveyMostRecentVersion() {
        // Get one survey (with the GUID), the most recent version (unpublished or published)
        
        Survey firstVersion = surveyDao.createSurvey(new SimpleSurvey());
        Survey middleVersion = surveyDao.versionSurvey(firstVersion);
        Survey finalVersion = surveyDao.versionSurvey(firstVersion);
        
        // Now confuse the matter by publishing a version before the last one.
        surveyDao.publishSurvey(middleVersion);
        
        Survey result = surveyDao.getSurveyMostRecentVersion(studyIdentifier, firstVersion.getGuid());
        assertEquals("Retrieves most recent version", finalVersion.getCreatedOn(), result.getCreatedOn());
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersion() {
        // Get one survey (with the GUID), the most recently published version
        
        Survey firstVersion = surveyDao.createSurvey(new SimpleSurvey());
        Survey middleVersion = surveyDao.versionSurvey(firstVersion);
        surveyDao.versionSurvey(firstVersion);
        
        // This is the version we want to retrieve now
        surveyDao.publishSurvey(middleVersion);
        
        Survey result = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, firstVersion.getGuid());
        assertEquals("Retrieves most recent version", middleVersion.getCreatedOn(), result.getCreatedOn());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getSurveyMostRecentlyPublishedVersionThrowsException() {
        Survey firstVersion = surveyDao.createSurvey(new SimpleSurvey());
        surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, firstVersion.getGuid());
    }
    
    @Test
    public void getAllSurveysMostRecentlyPublishedVersion() {
        // Get all surveys (complete set of the GUIDS, most recently published (if never published, GUID isn't included)
        Survey firstVersion = surveyDao.createSurvey(new SimpleSurvey());
        surveyDao.versionSurvey(firstVersion);
        surveyDao.versionSurvey(firstVersion);
        surveyDao.publishSurvey(firstVersion);
        
        Survey nextVersion = surveyDao.createSurvey(new SimpleSurvey());
        surveyDao.versionSurvey(nextVersion);
        nextVersion = surveyDao.versionSurvey(nextVersion);
        surveyDao.publishSurvey(nextVersion);
        
        // This should retrieve two surveys matching the references firstVersion & nextVersion
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
        
        assertEquals("Second survey is the correct version", nextVersion.getGuid(), surveys.get(0).getGuid());
        assertEquals("Second survey is the correct version", nextVersion.getCreatedOn(), surveys.get(0).getCreatedOn());
        
        assertEquals("First survey is the correct version", firstVersion.getGuid(), surveys.get(1).getGuid());
        assertEquals("First survey is the correct version", firstVersion.getCreatedOn(), surveys.get(1).getCreatedOn());
    }
    
    @Test
    public void getAllSurveysMostRecentVersion() {
        // Get all surveys (complete set of the GUIDS, most recent (published or unpublished)
        // Get all surveys (complete set of the GUIDS, most recently published (if never published, GUID isn't included)
        Survey firstVersion = surveyDao.createSurvey(new SimpleSurvey());
        firstVersion = surveyDao.versionSurvey(firstVersion);
        surveyDao.publishSurvey(firstVersion); // published is not the most recent
        firstVersion = surveyDao.versionSurvey(firstVersion);
        
        Survey nextVersion = surveyDao.createSurvey(new SimpleSurvey());
        surveyDao.versionSurvey(nextVersion);
        nextVersion = surveyDao.versionSurvey(nextVersion);
        surveyDao.publishSurvey(nextVersion); // published is again not the most recent.
        nextVersion = surveyDao.versionSurvey(nextVersion);
        
        // This should retrieve two surveys matching the references firstVersion & nextVersion
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentVersion(studyIdentifier);
        
        assertEquals("There should be two survey versions", 2, surveys.size());
        
        assertEquals("Second survey is the correct version, first in list", nextVersion.getGuid(), surveys.get(0).getGuid());
        assertEquals("Second survey is the correct version, first in list", nextVersion.getCreatedOn(), surveys.get(0).getCreatedOn());
        
        assertEquals("First survey is the correct version, second in list", firstVersion.getGuid(), surveys.get(1).getGuid());
        assertEquals("First survey is the correct version, second in list", firstVersion.getCreatedOn(), surveys.get(1).getCreatedOn());
    }
    
    @Test
    public void canGetAllSurveys() {
        surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.createSurvey(new TestSurvey(true));

        Survey survey = surveyDao.createSurvey(new TestSurvey(true));

        surveyDao.versionSurvey(survey);

        // Get all surveys
        
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentVersion(studyIdentifier);

        assertEquals("All most recent surveys are returned", 5, surveys.size());

        // Get all surveys of a version
        surveys = surveyDao.getSurveyAllVersions(studyIdentifier, survey.getGuid());
        assertEquals("All survey versions are returned", 2, surveys.size());

        Survey version1 = surveys.get(0);
        Survey version2 = surveys.get(1);
        assertEquals("Surveys have same GUID", version1.getGuid(), version2.getGuid());
        assertEquals("Surveys have same Study key", version1.getStudyIdentifier(), version2.getStudyIdentifier());
        assertNotEquals("Surveys have different createdOn attribute", version1.getCreatedOn(), version2.getCreatedOn());
    }

    // CLOSE SURVEY

    @Test
    public void canClosePublishedSurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        survey = surveyDao.publishSurvey(survey);

        survey = surveyDao.closeSurvey(survey);
        assertEquals("Survey no longer published", false, survey.isPublished());

        survey = surveyDao.getSurvey(survey);
        assertEquals("Survey no longer published", false, survey.isPublished());
    }

    // GET PUBLISHED SURVEY

    @Test
    public void canRetrieveMostRecentlyPublishedSurveysWithManyVersions() {
        // Version 1.
        Survey survey1 = surveyDao.createSurvey(new TestSurvey(true));

        // Version 2.
        Survey survey2 = surveyDao.versionSurvey(survey1);

        // Version 3 (tossed)
        surveyDao.versionSurvey(survey2);

        // Publish one version
        surveyDao.publishSurvey(survey1);

        List<Survey> surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
        assertEquals("Retrieved published testSurvey v1", survey1.getCreatedOn(), surveys.get(0).getCreatedOn());

        // Publish a later version
        surveyDao.publishSurvey(survey2);

        // Now the most recent version of this testSurvey should be survey2.
        surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
        assertEquals("Retrieved published testSurvey v2", survey2.getCreatedOn(), surveys.get(0).getCreatedOn());
    }

    @Test
    public void canRetrieveMostRecentPublishedSurveysWithManySurveys() {
        Survey survey1 = surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.publishSurvey(survey1);

        Survey survey2 = surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.publishSurvey(survey2);

        Survey survey3 = surveyDao.createSurvey(new TestSurvey(true));
        surveyDao.publishSurvey(survey3);

        List<Survey> published = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);

        assertEquals("There are three published surveys", 3, published.size());
        assertEquals("The first is survey3", survey3.getGuid(), published.get(0).getGuid());
        assertEquals("The middle is survey2", survey2.getGuid(), published.get(1).getGuid());
        assertEquals("The last is survey1", survey1.getGuid(), published.get(2).getGuid());
    }

    // DELETE SURVEY

    /*
    @Test(expected = PublishedSurveyException.class)
    public void cannotDeleteAPublishedSurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveyDao.publishSurvey(survey);

        surveyDao.deleteSurvey(studyIdentifier, survey);
    }*/
    
    @Test
    public void cannotDeletePublishedSurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        survey = surveyDao.publishSurvey(survey);
        try {
            surveyDao.deleteSurvey(studyIdentifier, survey);
            fail("Should have thrown an exception.");
        } catch(PublishedSurveyException e) {
            assertEquals("A published survey cannot be updated or deleted (only closed).", e.getMessage());
            assertEquals(survey, e.getSurvey());
        }
    }
    
    @Test
    public void cannotDeleteSurveyWithResponses() {
        try {
            Survey survey = surveyDao.createSurvey(testSurvey);
            survey = surveyDao.publishSurvey(survey);
            
            SurveyAnswer answer = new SurveyAnswer();
            answer.setAnsweredOn(DateTime.now().getMillis());
            answer.setClient("mobile");
            answer.setQuestionGuid(survey.getElements().get(0).getGuid());
            answer.setDeclined(false);
            answer.setAnswers(Lists.newArrayList("true"));
            
            surveyResponseDao.createSurveyResponse(survey, "BBB", Lists.newArrayList(answer), BridgeUtils.generateGuid());
            survey = surveyDao.closeSurvey(survey);
            
            surveyDao.deleteSurvey(studyIdentifier, survey);
            fail("Should have thrown an exception.");
            
        } catch(IllegalStateException e) {
            assertEquals("Survey has been answered by participants; it cannot be deleted.", e.getMessage());
        } finally {
            surveyResponseDao.deleteSurveyResponses("BBB");
        }
    }
    
    @Test
    public void cannotDeleteSurveyThatHasBeenScheduled() {
        SchedulePlan plan = null;
        try {
            Survey survey = surveyDao.createSurvey(testSurvey);
            survey = surveyDao.publishSurvey(survey);
            
            String url = String.format("https://webservices-%s.sagebridge.org/api/v2/surveys/%s/revisions/%s",
                BridgeConfigFactory.getConfig().getEnvironment().name().toLowerCase(), survey.getGuid(),
                new DateTime(survey.getCreatedOn()).toString());
            Activity activity = new Activity("Activty", url);
            
            Schedule schedule = new Schedule();
            schedule.addActivity(activity);
            schedule.setDelay("P1D");
            schedule.setInterval("P2D");
            schedule.setScheduleType(ScheduleType.RECURRING);
            schedule.setLabel("Schedule");
            
            SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
            strategy.setSchedule(schedule);
            
            plan = new DynamoSchedulePlan();
            plan.setStudyKey(studyIdentifier.getIdentifier());
            plan.setLabel("SchedulePlan");
            plan.setStrategy(strategy);
            plan = schedulePlanDao.createSchedulePlan(plan);
            
            survey = surveyDao.closeSurvey(survey);
            surveyDao.deleteSurvey(studyIdentifier, survey);
            fail("Should have thrown an exception.");
            
        } catch(IllegalStateException e) {
            assertEquals("Survey has been scheduled; it cannot be deleted.", e.getMessage());
        } finally {
            if (plan != null) {
                schedulePlanDao.deleteSchedulePlan(studyIdentifier, plan.getGuid());    
            }
        }
    }
}
