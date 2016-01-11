package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.GSI_WAIT_DURATION;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SurveyServiceTest {

    private static Logger logger = LoggerFactory.getLogger(SurveyServiceTest.class);
    
    @Resource
    UploadSchemaService schemaService;

    @Resource
    SurveyService surveyService;

    private TestSurvey testSurvey;
    private Set<GuidCreatedOnVersionHolderImpl> surveysToDelete;

    @Before
    public void before() {
        testSurvey = new TestSurvey(true);
        surveysToDelete = new HashSet<>();
    }

    @After
    public void after() {
        // clean up surveys
        for (GuidCreatedOnVersionHolder oneSurvey : surveysToDelete) {
            try {
                surveyService.deleteSurveyPermanently(oneSurvey);
            } catch (Exception ex) {
                logger.error(ex.getMessage(), ex);
            }
        }
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

    @Test
    public void createChangesAllGUIDs() {
        Set<String> originalGuids = Sets.newHashSet();
        
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        
        // Save all the identifiers
        originalGuids.add(survey.getGuid());
        for (SurveyElement element : survey.getElements()) {
            originalGuids.add(element.getGuid());
        }
        
        Survey alteredSurvey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(alteredSurvey));
        
        // verify all the identifiers
        assertFalse(originalGuids.contains(alteredSurvey.getGuid()));
        for (SurveyElement element : alteredSurvey.getElements()) {
            assertFalse(originalGuids.contains(element.getGuid()));
        }
    }

    @Test
    public void crudSurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        assertTrue("Survey has a guid", survey.getGuid() != null);
        assertTrue("Survey has been versioned", survey.getCreatedOn() != 0L);
        assertTrue("Question #1 has a guid", survey.getElements().get(0).getGuid() != null);
        assertTrue("Question #2 has a guid", survey.getElements().get(1).getGuid() != null);
        
        SurveyQuestion question = (SurveyQuestion)survey.getElements().get(0);

        survey.setIdentifier("newIdentifier");
        surveyService.updateSurvey(survey);
        survey = surveyService.getSurvey(survey);
        assertEquals("Identifier has been changed", "newIdentifier", survey.getIdentifier());
        assertEquals("Be honest: do you have high blood pressue?", question.getPromptDetail());
        surveyService.deleteSurvey(survey);

        try {
            surveyService.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid());
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException enfe) {
            // expected exception
        }
    }

    // UPDATE SURVEY

    @Test
    public void canUpdateASurveyVersion() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        Survey nextVersion = surveyService.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(nextVersion));

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
        Survey finalVersion = surveyService.getSurvey(nextVersion);
        assertEquals("Next version has same identifier", nextVersion.getIdentifier(), finalVersion.getIdentifier());
        assertEquals("Next name has not changed", nextVersion.getName(), finalVersion.getName());
    }

    @Test
    public void crudSurveyQuestions() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

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
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        survey.setVersion(44L);
        surveyService.updateSurvey(survey);
    }

    @Test(expected = PublishedSurveyException.class)
    public void cannotUpdatePublishedSurveys() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        surveyService.publishSurvey(TEST_STUDY, survey);

        survey.setName("This is a new name");
        surveyService.updateSurvey(survey);
    }

    // VERSION SURVEY

    @Test
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        surveyService.publishSurvey(TEST_STUDY, survey);

        Long originalVersion = survey.getCreatedOn();
        survey = surveyService.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        assertEquals("Newly versioned testSurvey is not published", false, survey.isPublished());

        Long newVersion = survey.getCreatedOn();
        assertNotEquals("Versions differ", newVersion, originalVersion);
    }

    @Test
    public void versioningASurveyCopiesTheQuestions() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        String v1SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v1Guid = survey.getElements().get(0).getGuid();

        survey = surveyService.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        String v2SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v2Guid = survey.getElements().get(0).getGuid();

        assertNotEquals("Survey reference differs", v1SurveyCompoundKey, v2SurveyCompoundKey);
        assertNotEquals("Survey question GUID differs", v1Guid, v2Guid);
    }

    // PUBLISH SURVEY

    @Test
    public void canPublishASurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        survey = surveyService.publishSurvey(TEST_STUDY, survey);

        assertTrue("Survey is marked published", survey.isPublished());

        Survey pubSurvey = surveyService.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid());

        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // Publishing again is harmless
        survey = surveyService.publishSurvey(TEST_STUDY, survey);
        pubSurvey = surveyService.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid());
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());
    }

    @Test
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        survey = surveyService.publishSurvey(TEST_STUDY, survey);

        Survey laterSurvey = surveyService.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(laterSurvey));
        assertNotEquals("Surveys do not have the same createdOn", survey.getCreatedOn(),
                laterSurvey.getCreatedOn());

        laterSurvey = surveyService.publishSurvey(TEST_STUDY, laterSurvey);

        Survey pubSurvey = surveyService.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid());
        assertEquals("Later testSurvey is the published testSurvey", laterSurvey.getCreatedOn(), pubSurvey.getCreatedOn());
    }

    // GET SURVEYS

    @Test
    public void failToGetSurveysByBadStudyKey() {
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("foo");
        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(studyIdentifier);
        assertEquals("No surveys", 0, surveys.size());
    }

    @Test
    public void canGetAllSurveys() throws Exception {
        Set<GuidCreatedOnVersionHolderImpl> mostRecentVersionSurveys = new HashSet<>();
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(surveyService.createSurvey(new TestSurvey(true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(surveyService.createSurvey(new TestSurvey(true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(surveyService.createSurvey(new TestSurvey(true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(surveyService.createSurvey(new TestSurvey(true))));

        Survey survey = surveyService.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        Survey nextVersion = surveyService.versionSurvey(survey);
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(nextVersion));
        surveysToDelete.addAll(mostRecentVersionSurveys);

        Thread.sleep(GSI_WAIT_DURATION);
        // Get all surveys
        // Make sure this returns all surveys that we created
        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(TEST_STUDY);
        assertContainsAllKeys(mostRecentVersionSurveys, surveys);

        // Get all surveys of a version
        surveys = surveyService.getSurveyAllVersions(TEST_STUDY, survey.getGuid());
        assertEquals("All surveys are returned", 2, surveys.size());

        Survey version1 = surveys.get(0);
        Survey version2 = surveys.get(1);
        assertEquals("Surveys have same GUID", version1.getGuid(), version2.getGuid());
        assertEquals("Surveys have same Study key", version1.getStudyIdentifier(), version2.getStudyIdentifier());
        assertNotEquals("Surveys have different createdOn attribute", version1.getCreatedOn(),
                version2.getCreatedOn());
    }

    // GET PUBLISHED SURVEY

    @Test
    public void canRetrieveMostRecentlyPublishedSurveysWithManyVersions() throws Exception {
        // Version 1.
        Survey survey1 = surveyService.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey1));

        // Version 2.
        Survey survey2 = surveyService.versionSurvey(survey1);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey2));

        // Version 3 (tossed)
        Survey survey3 = surveyService.versionSurvey(survey2);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey3));

        // Publish one version
        surveyService.publishSurvey(TEST_STUDY, survey1);

        // Must pause because the underlying query uses a global secondary index, and
        // this does not support consistent reads
        Thread.sleep(GSI_WAIT_DURATION);
        // Find the survey that we created and make sure it's the published version (survey1)
        List<Survey> surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY);
        boolean foundSurvey1 = false;
        for (Survey oneSurvey : surveys) {
            if (oneSurvey.keysEqual(survey1)) {
                foundSurvey1 = true;
                assertEquals("Retrieved published testSurvey v1", survey1.getCreatedOn(), oneSurvey.getCreatedOn());
            }
        }
        assertTrue(foundSurvey1);

        // Publish a later version
        surveyService.publishSurvey(TEST_STUDY, survey2);
        
        // Must pause because the underlying query uses a global secondary index, and
        // this does not support consistent reads
        Thread.sleep(GSI_WAIT_DURATION);
        // Now the most recent version of this testSurvey should be survey2.
        surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY);
        boolean foundSurvey2 = false;
        for (Survey oneSurvey : surveys) {
            if (oneSurvey.keysEqual(survey2)) {
                foundSurvey2 = true;
                assertEquals("Retrieved published testSurvey v2", survey2.getCreatedOn(), oneSurvey.getCreatedOn());
            }
        }
        assertTrue(foundSurvey2);
    }

    @Test
    public void canRetrieveMostRecentPublishedSurveysWithManySurveys() throws Exception {
        Survey survey1 = surveyService.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey1));
        surveyService.publishSurvey(TEST_STUDY, survey1);

        Survey survey2 = surveyService.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey2));
        surveyService.publishSurvey(TEST_STUDY, survey2);

        Survey survey3 = surveyService.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey3));
        surveyService.publishSurvey(TEST_STUDY, survey3);

        // Must pause because the underlying query uses a global secondary index, and
        // this does not support consistent reads
        Thread.sleep(GSI_WAIT_DURATION);
        // Make sure this returns all surveys that we created
        List<Survey> published = surveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY);
        assertContainsAllKeys(surveysToDelete, published);
    }

    // DELETE SURVEY

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
            assertEquals("Survey is invalid: elements[0].identifier is required; elements[0].title is required; elements[0].prompt is required; elements[0].image.source must be a valid URL to an image; elements[0].image.width is required; elements[0].image.height is required", e.getMessage());
        }
    }

    private static void assertContainsAllKeys(Set<GuidCreatedOnVersionHolderImpl> expected, List<Survey> actual) {
        for (GuidCreatedOnVersionHolderImpl oneExpected : expected) {
            boolean found = false;
            for (Survey oneActual : actual) {
                if (oneExpected.keysEqual(oneActual)) {
                    found = true;
                    break;
                }
            }
            assertTrue("Found survey " + oneExpected, found);
        }
    }
}
