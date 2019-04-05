package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.GSI_WAIT_DURATION;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Resource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
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

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SurveyServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(SurveyServiceTest.class);

    @Resource
    UploadSchemaService schemaService;

    @Resource
    SurveyService surveyService;

    private SharedModuleMetadataService mockSharedModuleMetadataService;

    private String surveyId;
    private TestSurvey testSurvey;
    private Set<GuidCreatedOnVersionHolderImpl> surveysToDelete;

    @Before
    public void before() {
        mockSharedModuleMetadataService = mock(SharedModuleMetadataService.class);
        when(mockSharedModuleMetadataService.queryAllMetadata(anyBoolean(), anyBoolean(), anyString(), any(),
                anySetOf(String.class), anyBoolean())).thenReturn(ImmutableList.of());

        surveyId = TestUtils.randomName(SurveyServiceTest.class);
        testSurvey = new TestSurvey(SurveyServiceTest.class, true);
        testSurvey.setIdentifier(surveyId);

        surveysToDelete = new HashSet<>();

        surveyService.setSharedModuleMetadataService(mockSharedModuleMetadataService);
        schemaService.setSharedModuleMetadataService(mockSharedModuleMetadataService);
    }

    @After
    public void after() {
        // clean up surveys
        for (GuidCreatedOnVersionHolder oneSurvey : surveysToDelete) {
            try {
                surveyService.deleteSurveyPermanently(TEST_STUDY, oneSurvey);
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

        // Create another survey. Change the identifier so they don't conflict.
        testSurvey.setIdentifier(TestUtils.randomName(SurveyServiceTest.class));
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
        surveyService.updateSurvey(TEST_STUDY, survey);
        survey = surveyService.getSurvey(TEST_STUDY, survey, true, true);
        assertEquals("Identifier has not been changed", surveyId, survey.getIdentifier());
        assertEquals("Be honest: do you have high blood pressue?", question.getPromptDetail());
        surveyService.deleteSurvey(TEST_STUDY, survey);

        try {
            surveyService.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid(), false);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException enfe) {
            // expected exception
        }
    }
    
    @Test
    public void copySurvey() throws Exception {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        // mess with some fields
        survey.setDeleted(true);
        survey.setPublished(true);
        
        // Make a copy with all the same data:
        String json = BridgeObjectMapper.get().writeValueAsString(survey);
        Survey survey2 = BridgeObjectMapper.get().readValue(json, Survey.class);

        // This is JsonIgnored, so add it back.
        survey2.setStudyIdentifier(survey.getStudyIdentifier());

        // Set a different survey ID so they don't conflict.
        survey2.setIdentifier(TestUtils.randomName(SurveyServiceTest.class));
        
        survey2 = surveyService.createSurvey(survey2);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey2));
        
        assertNotEquals(survey.getGuid(), survey2.getGuid());
        assertNotEquals(survey.getCreatedOn(), survey2.getCreatedOn());
        assertNotEquals(survey.getModifiedOn(), survey2.getModifiedOn());
        assertEquals((Long)1L, survey.getVersion());
        assertEquals((Long)1L, survey2.getVersion());
        assertFalse(survey2.isDeleted());
        assertFalse(survey2.isPublished());
        for (int i=0; i < survey.getElements().size(); i++) {
            SurveyElement el1 = survey.getElements().get(i);
            SurveyElement el2 = survey2.getElements().get(i);
            assertNotEquals(el1.getGuid(), el2.getGuid());
        }
    }

    // UPDATE SURVEY

    @Test
    public void canUpdateASurveyVersion() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        Survey nextVersion = surveyService.versionSurvey(TEST_STUDY, survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(nextVersion));

        // If you change these, it looks like a different testSurvey, you'll just get a not found exception.
        // testSurvey.setGuid("A");
        // testSurvey.setStudyKey("E");
        // testSurvey.setCreatedOn(new DateTime().getMillis());
        survey.setIdentifier("B");
        survey.setName("C");

        surveyService.updateSurvey(TEST_STUDY, survey);
        survey = surveyService.getSurvey(TEST_STUDY, survey, true, true);

        assertEquals("Identifier cannot be updated", surveyId, survey.getIdentifier());
        assertEquals("Name can be updated", "C", survey.getName());

        // Now verify the nextVersion has not been changed
        Survey finalVersion = surveyService.getSurvey(TEST_STUDY, nextVersion, true, true);
        assertEquals("Next version has same identifier", surveyId, finalVersion.getIdentifier());
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
        surveyService.updateSurvey(TEST_STUDY, survey);

        survey = surveyService.getSurvey(TEST_STUDY, survey, true, true);

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
        surveyService.updateSurvey(TEST_STUDY, survey);
    }

    @Test(expected = PublishedSurveyException.class)
    public void cannotUpdatePublishedSurveys() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        surveyService.publishSurvey(TEST_STUDY, survey, false);

        survey.setName("This is a new name");
        surveyService.updateSurvey(TEST_STUDY, survey);
    }

    // VERSION SURVEY

    @Test
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        surveyService.publishSurvey(TEST_STUDY, survey, false);

        Long originalVersion = survey.getCreatedOn();
        survey = surveyService.versionSurvey(TEST_STUDY, survey);
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

        survey = surveyService.versionSurvey(TEST_STUDY, survey);
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
        survey = surveyService.publishSurvey(TEST_STUDY, survey, false);

        assertTrue("Survey is marked published", survey.isPublished());

        Survey pubSurvey = surveyService.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid(), true);

        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // Publishing again is harmless
        survey = surveyService.publishSurvey(TEST_STUDY, survey, false);
        pubSurvey = surveyService.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid(), true);
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());
    }

    @Test
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = surveyService.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        survey = surveyService.publishSurvey(TEST_STUDY, survey, false);

        Survey laterSurvey = surveyService.versionSurvey(TEST_STUDY, survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(laterSurvey));
        assertNotEquals("Surveys do not have the same createdOn", survey.getCreatedOn(),
                laterSurvey.getCreatedOn());

        laterSurvey = surveyService.publishSurvey(TEST_STUDY, laterSurvey, false);

        Survey pubSurvey = surveyService.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid(), true);
        assertEquals("Later testSurvey is the published testSurvey", laterSurvey.getCreatedOn(), pubSurvey.getCreatedOn());
    }

    // GET SURVEYS

    @Test
    public void failToGetSurveysByBadStudyKey() {
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("foo");
        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(studyIdentifier, false);
        assertEquals("No surveys", 0, surveys.size());
    }

    @Test
    public void canGetAllSurveys() throws Exception {
        Set<GuidCreatedOnVersionHolderImpl> mostRecentVersionSurveys = new HashSet<>();
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true))));

        Survey survey = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        Survey nextVersion = surveyService.versionSurvey(TEST_STUDY, survey);
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(nextVersion));
        surveysToDelete.addAll(mostRecentVersionSurveys);

        Thread.sleep(GSI_WAIT_DURATION);
        // Get all surveys
        // Make sure this returns all surveys that we created
        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(TEST_STUDY, false);
        assertContainsAllKeys(mostRecentVersionSurveys, surveys);

        // Get all surveys of a version
        surveys = surveyService.getSurveyAllVersions(TEST_STUDY, survey.getGuid(), false);
        assertEquals("All surveys are returned", 2, surveys.size());

        Survey version1 = surveys.get(0);
        Survey version2 = surveys.get(1);
        assertEquals("Surveys have same GUID", version1.getGuid(), version2.getGuid());
        assertEquals("Surveys have same Study key", version1.getStudyIdentifier(), version2.getStudyIdentifier());
        assertNotEquals("Surveys have different createdOn attribute", version1.getCreatedOn(),
                version2.getCreatedOn());
        
        Survey toDelete = surveys.get(0);
        surveyService.deleteSurvey(TEST_STUDY, toDelete);
        
        assertTrue(surveyService.getSurveyAllVersions(TEST_STUDY, survey.getGuid(), true).stream().anyMatch(Survey::isDeleted));
        assertTrue(surveyService.getSurveyAllVersions(TEST_STUDY, survey.getGuid(), false).stream().noneMatch(Survey::isDeleted));
        
        surveyService.deleteSurveyPermanently(TEST_STUDY, new GuidCreatedOnVersionHolderImpl(toDelete));
        assertTrue(surveyService.getSurveyAllVersions(TEST_STUDY, survey.getGuid(), true).stream().noneMatch(Survey::isDeleted));
        assertTrue(surveyService.getSurveyAllVersions(TEST_STUDY, survey.getGuid(), false).stream().noneMatch(Survey::isDeleted));
    }

    // GET PUBLISHED SURVEY

    @Test
    public void canRetrieveMostRecentlyPublishedSurveysWithManyVersions() throws Exception {
        // Version 1.
        Survey survey1 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey1));

        // Version 2.
        Survey survey2 = surveyService.versionSurvey(TEST_STUDY, survey1);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey2));

        // Version 3 (tossed)
        Survey survey3 = surveyService.versionSurvey(TEST_STUDY, survey2);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey3));

        // Publish one version
        surveyService.publishSurvey(TEST_STUDY, survey1, false);

        // Must pause because the underlying query uses a global secondary index, and
        // this does not support consistent reads
        Thread.sleep(GSI_WAIT_DURATION);
        // Find the survey that we created and make sure it's the published version (survey1)
        List<Survey> surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false);
        boolean foundSurvey1 = false;
        for (Survey oneSurvey : surveys) {
            if (oneSurvey.keysEqual(survey1)) {
                foundSurvey1 = true;
                assertEquals("Retrieved published testSurvey v1", survey1.getCreatedOn(), oneSurvey.getCreatedOn());
            }
        }
        assertTrue(foundSurvey1);

        // Publish a later version
        surveyService.publishSurvey(TEST_STUDY, survey2, false);
        
        // Must pause because the underlying query uses a global secondary index, and
        // this does not support consistent reads
        Thread.sleep(GSI_WAIT_DURATION);
        // Now the most recent version of this testSurvey should be survey2.
        surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false);
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
    public void getAllSurveysMostRecentlyPublishedVersionIncludeDeletedFlag() {
        // Version A1 
        Survey surveyA1 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyA1));
        
        // Version B1 
        Survey surveyB1 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyB1));
        
        // publish the versions
        surveyService.publishSurvey(TEST_STUDY, new GuidCreatedOnVersionHolderImpl(surveyA1), false);
        surveyService.publishSurvey(TEST_STUDY, new GuidCreatedOnVersionHolderImpl(surveyB1), false);
        
        // delete one of the published versions right now
        surveyService.deleteSurvey(TEST_STUDY, new GuidCreatedOnVersionHolderImpl(surveyA1));
        
        assertTrue(surveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false).stream().noneMatch(Survey::isDeleted));
        assertTrue(surveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, true).stream().anyMatch(Survey::isDeleted));
    }
    
    @Test
    public void getAllSurveysMostRecentVersionIncludeDeletedFlag() {
        // Version A1 
        Survey surveyA1 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyA1));
        
        // Version A2
        Survey surveyA2 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyA2));
        
        // Version B1 
        Survey surveyB1 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyB1));
        
        // Version B2
        Survey surveyB2 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyB2));
        
        // We return the most recent version whether deleted or not when the flag is true, so we should see this
        surveyService.deleteSurvey(TEST_STUDY, new GuidCreatedOnVersionHolderImpl(surveyA1));
        assertTrue(surveyService.getAllSurveysMostRecentVersion(TEST_STUDY, false).stream().noneMatch(Survey::isDeleted));
        assertTrue(surveyService.getAllSurveysMostRecentVersion(TEST_STUDY, true).stream().anyMatch(Survey::isDeleted));
    }

    @Test
    public void canRetrieveMostRecentPublishedSurveysWithManySurveys() throws Exception {
        Survey survey1 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey1));
        surveyService.publishSurvey(TEST_STUDY, survey1, false);

        Survey survey2 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey2));
        surveyService.publishSurvey(TEST_STUDY, survey2, false);

        Survey survey3 = surveyService.createSurvey(new TestSurvey(SurveyServiceTest.class, true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey3));
        surveyService.publishSurvey(TEST_STUDY, survey3, false);

        // Must pause because the underlying query uses a global secondary index, and
        // this does not support consistent reads
        Thread.sleep(GSI_WAIT_DURATION);
        // Make sure this returns all surveys that we created
        List<Survey> published = surveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false);
        assertContainsAllKeys(surveysToDelete, published);
    }

    // DELETE SURVEY

    @Test
    public void validationInfoScreen() {
        Survey survey = new TestSurvey(SurveyServiceTest.class, true);
        
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
