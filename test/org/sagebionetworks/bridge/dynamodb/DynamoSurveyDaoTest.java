package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.GSI_WAIT_DURATION;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.services.UploadSchemaService;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSurveyDaoTest {

    private static Logger logger = LoggerFactory.getLogger(DynamoSurveyDaoTest.class);

    @Resource
    DynamoSurveyDao surveyDao;

    @Resource
    UploadSchemaService uploadSchemaService;

    private TestSurvey testSurvey;
    private Set<GuidCreatedOnVersionHolderImpl> surveysToDelete;

    @Before
    public void before() {
        testSurvey = new TestSurvey(DynamoSurveyDaoTest.class, true);
        // remove all but two questions to reduce DDB usage.
        testSurvey.setElements(testSurvey.getElements().subList(0, 2));
        surveysToDelete = new HashSet<>();
    }

    @After
    public void after() {
        // clean up surveys
        for (GuidCreatedOnVersionHolder oneSurvey : surveysToDelete) {
            try {
                surveyDao.deleteSurveyPermanently(oneSurvey);
            } catch (Exception ex) {
                logger.error(ex.getMessage(), ex);
            }
        }
    }

    // Helper methods to ensure we always record these calls for cleanup
    
    private Survey createSurvey(Survey survey) {
        Survey savedSurvey = surveyDao.createSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(savedSurvey));
        return savedSurvey;
    }
    
    private Survey versionSurvey(Survey survey) {
        Survey versionedSurvey = surveyDao.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(versionedSurvey));
        return versionedSurvey;
    }
    
    private Survey publishSurvey(StudyIdentifier studyIdentifier, Survey survey) {
        Survey publishedSurvey = surveyDao.publishSurvey(studyIdentifier, survey, false);
        return publishedSurvey;
    }

    private class SimpleSurvey extends DynamoSurvey {
        public SimpleSurvey(String name) {
            setName(name);
            setIdentifier(TestUtils.randomName(DynamoSurveyDaoTest.class));
            setStudyIdentifier(TEST_STUDY_IDENTIFIER);

            // need at least one element
            DynamoSurveyQuestion question = new DynamoSurveyQuestion();
            question.setIdentifier("test-q");
            question.setUiHint(UIHint.CHECKBOX);
            question.setPrompt("Yes or No?");
            question.setConstraints(new BooleanConstraints());
            setElements(ImmutableList.of(question));
        }
    }
    
    // CREATE SURVEY

    // Not an ideal test, but this is thrown from a precondition, nothing changes
    @Test(expected = NullPointerException.class)
    public void createPreventsEmptyStudyKey() {
        testSurvey.setStudyIdentifier(null);
        surveyDao.createSurvey(testSurvey);
    }

    @Test
    public void crudSurvey() {
        Survey survey = createSurvey(testSurvey);

        assertTrue("Survey has a guid", survey.getGuid() != null);
        assertTrue("Survey has been versioned", survey.getCreatedOn() != 0L);
        assertTrue("Question #1 has a guid", survey.getElements().get(0).getGuid() != null);
        assertTrue("Question #2 has a guid", survey.getElements().get(1).getGuid() != null);
        assertNull(survey.getSchemaRevision());

        // These fields are updateable.
        survey.setIdentifier("newIdentifier");
        survey.setName("New Name");
        survey.setCopyrightNotice("New notice");

        // These fields are not.
        long originalModifiedOn = survey.getModifiedOn();
        survey.setStudyIdentifier("foobar");
        survey.setPublished(true);
        survey.setModifiedOn(1337L);
        survey.setSchemaRevision(42);

        // guid and createdOn can't be changed, since those are keys
        // version is tested in cannotUpdateVersionWithoutException()
        // elements is tested in crudSurveyQuestions()

        surveyDao.updateSurvey(survey);
        Survey updatedSurvey = surveyDao.getSurvey(survey, true);

        // Verify fields updated.
        assertEquals("Identifier has been changed", "newIdentifier", updatedSurvey.getIdentifier());
        assertEquals("New Name", updatedSurvey.getName());
        assertEquals("New notice", updatedSurvey.getCopyrightNotice());

        // Verify fields not updated.
        assertEquals(TEST_STUDY_IDENTIFIER, updatedSurvey.getStudyIdentifier());
        assertFalse(updatedSurvey.isPublished());

        // Verify modified on updated internally.
        assertNotEquals(originalModifiedOn, updatedSurvey.getModifiedOn());
        assertNull(updatedSurvey.getSchemaRevision());

        survey.setVersion(updatedSurvey.getVersion());
        surveyDao.deleteSurvey(survey);

        Survey retrieved = surveyDao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid(), true);
        assertNull(retrieved);
    }

    // UPDATE SURVEY

    @Test
    public void canUpdateASurveyVersion() {
        Survey survey = createSurvey(testSurvey);

        Survey nextVersion = versionSurvey(survey);

        // If you change these, it looks like a different testSurvey, you'll just get a not found exception.
        // testSurvey.setGuid("A");
        // testSurvey.setStudyKey("E");
        // testSurvey.setCreatedOn(new DateTime().getMillis());
        survey.setIdentifier("B");
        survey.setName("C");

        surveyDao.updateSurvey(survey);
        survey = surveyDao.getSurvey(survey, true);

        assertEquals("Identifier can be updated", "B", survey.getIdentifier());
        assertEquals("Name can be updated", "C", survey.getName());

        // Now verify the nextVersion has not been changed
        Survey finalVersion = surveyDao.getSurvey(nextVersion, true);
        assertEquals("Next version has same identifier", nextVersion.getIdentifier(), finalVersion.getIdentifier());
        assertEquals("Next name has not changed", nextVersion.getName(), finalVersion.getName());
    }

    @Test
    public void crudSurveyQuestions() {
        Survey survey = createSurvey(testSurvey);

        int count = survey.getElements().size();
        
        // Now, alter these, and verify they are altered
        survey.getElements().remove(0);
        survey.getElements().get(0).setIdentifier("new gender");
        surveyDao.updateSurvey(survey);

        survey = surveyDao.getSurvey(survey, true);

        assertEquals("Survey has one less question", count-1, survey.getElements().size());
        
        SurveyQuestion restored = (SurveyQuestion)survey.getElements().get(0);
        DateConstraints dc = (DateConstraints)restored.getConstraints();
        
        assertEquals("Survey has updated the one question's identifier", "new gender", restored.getIdentifier());
        
        assertNotNull("Constraints have earliestValue", dc.getEarliestValue());
        assertNotNull("Constraints have latestValue", dc.getLatestValue());
        assertEquals("Question has the correct UIHint", UIHint.DATEPICKER, restored.getUiHint());
    }

    @Test(expected = ConcurrentModificationException.class)
    public void cannotUpdateVersionWithoutException() {
        Survey survey = createSurvey(testSurvey);

        survey.setVersion(44L);
        surveyDao.updateSurvey(survey);
    }

    // VERSION SURVEY

    @Test
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = createSurvey(testSurvey);

        Survey publishedSurvey = publishSurvey(TEST_STUDY, survey);
        assertNotNull(publishedSurvey.getSchemaRevision());

        Long originalVersion = survey.getCreatedOn();
        survey = versionSurvey(survey);

        assertEquals("Newly versioned testSurvey is not published", false, survey.isPublished());
        assertNull(survey.getSchemaRevision());

        Long newVersion = survey.getCreatedOn();
        assertNotEquals("Versions differ", newVersion, originalVersion);
    }

    @Test
    public void versioningASurveyCopiesTheQuestions() {
        Survey survey = createSurvey(testSurvey);
        String v1SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v1Guid = survey.getElements().get(0).getGuid();

        survey = versionSurvey(survey);
        String v2SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v2Guid = survey.getElements().get(0).getGuid();

        assertNotEquals("Survey reference differs", v1SurveyCompoundKey, v2SurveyCompoundKey);
        assertNotEquals("Survey question GUID differs", v1Guid, v2Guid);
    }

    // PUBLISH SURVEY

    @Test
    public void canPublishASurvey() {
        Survey survey = createSurvey(testSurvey);
        survey = publishSurvey(TEST_STUDY, survey);

        assertTrue("Survey is marked published", survey.isPublished());

        // validate the corresponding schema was created
        UploadSchema uploadSchema = uploadSchemaService.getUploadSchema(TEST_STUDY, survey.getIdentifier());
        int schemaRev = uploadSchema.getRevision();

        assertEquals(survey.getIdentifier(), uploadSchema.getSchemaId());
        assertEquals(survey.getSchemaRevision().intValue(), uploadSchema.getRevision());
        assertEquals(survey.getName(), uploadSchema.getName());
        assertEquals(UploadSchemaType.IOS_SURVEY, uploadSchema.getSchemaType());

        List<UploadFieldDefinition> fieldDefList = uploadSchema.getFieldDefinitions();
        assertEquals(2, fieldDefList.size());

        assertEquals("high_bp", fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.BOOLEAN, fieldDefList.get(0).getType());

        assertEquals("last_checkup", fieldDefList.get(1).getName());
        assertEquals(UploadFieldType.CALENDAR_DATE, fieldDefList.get(1).getType());

        // validate get most recently published survey
        Survey pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid(), true);
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // Publishing again is harmless
        survey = publishSurvey(TEST_STUDY, survey);
        pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid(), true);
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // publishing an already published survey won't bump the schema rev
        assertEquals(schemaRev, pubSurvey.getSchemaRevision().intValue());
    }

    @Test
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = createSurvey(testSurvey);
        survey = publishSurvey(TEST_STUDY, survey);

        Survey laterSurvey = versionSurvey(survey);
        assertNotEquals("Surveys do not have the same createdOn", survey.getCreatedOn(),
                laterSurvey.getCreatedOn());

        laterSurvey = publishSurvey(TEST_STUDY, laterSurvey);

        Survey pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid(), true);
        
        assertEquals("Later testSurvey is the published testSurvey", laterSurvey.getCreatedOn(), pubSurvey.getCreatedOn());
    }

    // GET SURVEYS
    
    @Test
    public void failToGetSurveysByBadStudyKey() {
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentVersion(new StudyIdentifierImpl("foo"), false);
        assertEquals("No surveys", 0, surveys.size());
    }

    @Test
    public void getSurveyAllVersionsExcludeDeleted() {
        // Get a survey (one GUID), and no other surveys, all the versions, ordered most to least recent
        createSurvey(new SimpleSurvey("First Survey")); // spurious survey
        
        Survey versionedSurvey = createSurvey(new SimpleSurvey("Second Survey"));
        versionSurvey(versionedSurvey);
        versionSurvey(versionedSurvey);
        Survey finalVersion = versionSurvey(versionedSurvey);
        surveyDao.deleteSurvey(versionedSurvey);
        
        long lastCreatedOnTime = finalVersion.getCreatedOn();
        
        List<Survey> surveyVersions = surveyDao.getSurveyAllVersions(TEST_STUDY, versionedSurvey.getGuid(), false);

        assertTrue("No versions are deleted", surveyVersions.stream().noneMatch(Survey::isDeleted));
        assertTrue("All surveys versions of one survey",
                surveyVersions.stream().allMatch((survey) -> survey.getGuid().equals(versionedSurvey.getGuid())));
        assertEquals("First survey is the most recently versioned", lastCreatedOnTime, surveyVersions.get(0).getCreatedOn());
        assertNotEquals("createdOn updated", lastCreatedOnTime, versionedSurvey.getCreatedOn());
    }
    
    @Test
    public void getSurveyAllVersionsIncludeDeleted() {
        // Get a survey (one GUID), and no other surveys, all the versions, ordered most to least recent
        createSurvey(new SimpleSurvey("First Survey")); // spurious survey
        
        Survey versionedSurvey = createSurvey(new SimpleSurvey("Second Survey"));
        versionSurvey(versionedSurvey);
        versionSurvey(versionedSurvey);
        Survey finalVersion = versionSurvey(versionedSurvey);
        surveyDao.deleteSurvey(versionedSurvey);
        
        long lastCreatedOnTime = finalVersion.getCreatedOn();
        
        List<Survey> surveyVersions = surveyDao.getSurveyAllVersions(TEST_STUDY, versionedSurvey.getGuid(), true);
        
        assertTrue("One survey version is deleted", surveyVersions.stream().anyMatch(Survey::isDeleted));
        assertTrue("All surveys versions of one survey",
                surveyVersions.stream().allMatch((survey) -> survey.getGuid().equals(versionedSurvey.getGuid())));
        assertEquals("First survey is the most recently versioned", lastCreatedOnTime, surveyVersions.get(0).getCreatedOn());
        assertNotEquals("createdOn updated", lastCreatedOnTime, versionedSurvey.getCreatedOn());
    }
    
    @Test
    public void getSurveyWithoutElements() {
        Survey keys = createSurvey(new SimpleSurvey("First Survey"));
        
        Survey foundWithElements = surveyDao.getSurvey(keys, true);
        assertEquals(1, foundWithElements.getElements().size());
        
        Survey foundWithoutElements = surveyDao.getSurvey(keys, false);
        assertEquals(0, foundWithoutElements.getElements().size());
    }
    
    @Test
    public void getSurveyMostRecentVersion() {
        // Get one survey (with the GUID), the most recent version (unpublished or published)
        
        Survey firstVersion = createSurvey(new SimpleSurvey("First Survey"));
        Survey middleVersion = versionSurvey(firstVersion);
        Survey finalVersion = versionSurvey(firstVersion);

        // Now confuse the matter by publishing a version before the last one.
        publishSurvey(TEST_STUDY, middleVersion);

        Survey result = surveyDao.getSurveyMostRecentVersion(TEST_STUDY, firstVersion.getGuid());
        assertEquals("Retrieves most recent version", finalVersion.getCreatedOn(), result.getCreatedOn());
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersion() {
        // Get one survey (with the GUID), the most recently published version
        
        Survey firstVersion = createSurvey(new SimpleSurvey("First Survey"));
        Survey middleVersion = versionSurvey(firstVersion);
        versionSurvey(firstVersion);
        
        // This is the version we want to retrieve now
        publishSurvey(TEST_STUDY, middleVersion);

        Survey result = surveyDao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, firstVersion.getGuid(), true);
        assertEquals("Retrieves most recent version", middleVersion.getCreatedOn(), result.getCreatedOn());
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersionWithoutElements() {
        // Get one survey (with the GUID), the most recently published version
        
        Survey firstVersion = createSurvey(new SimpleSurvey("First Survey"));
        Survey middleVersion = versionSurvey(firstVersion);
        versionSurvey(firstVersion);
        
        // This is the version we want to retrieve now
        publishSurvey(TEST_STUDY, middleVersion);

        Survey result = surveyDao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, firstVersion.getGuid(), false);
        assertEquals("Retrieves most recent version", middleVersion.getCreatedOn(), result.getCreatedOn());
        assertEquals(0, result.getElements().size());
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersionReturnsNullWhenNotFound() {
        Survey firstVersion = createSurvey(new SimpleSurvey("First Survey"));
        Survey retrieved = surveyDao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, firstVersion.getGuid(), true);
        assertNull(retrieved);
    }
    
    @Test
    public void getAllSurveysMostRecentlyPublishedVersion() throws Exception {
        int initialCount = surveyDao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false).size();
        
        // Get all surveys (complete set of the GUIDS, most recently published (if never published, GUID isn't included)
        Survey firstSurvey = createSurvey(new SimpleSurvey("First Survey"));
        firstSurvey = versionSurvey(firstSurvey);
        firstSurvey = versionSurvey(firstSurvey);
        firstSurvey = publishSurvey(TEST_STUDY, firstSurvey);

        Survey secondSurvey = createSurvey(new SimpleSurvey("Second Survey"));
        secondSurvey = versionSurvey(secondSurvey);
        secondSurvey = versionSurvey(secondSurvey);
        secondSurvey = publishSurvey(TEST_STUDY, secondSurvey);
        
        // This version is later, and should be returned
        secondSurvey = versionSurvey(secondSurvey);
        secondSurvey = publishSurvey(TEST_STUDY, secondSurvey);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // This should include firstVersion and nextVersion.
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false);
        assertEquals(initialCount+2, surveys.size());
        assertContainsAllKeys(ImmutableSet.of(new GuidCreatedOnVersionHolderImpl(firstSurvey),
                new GuidCreatedOnVersionHolderImpl(secondSurvey)), surveys);
    }
    
    @Test
    public void getAllSurveysMostRecentVersion() throws Exception {
        int initialCount = surveyDao.getAllSurveysMostRecentVersion(TEST_STUDY, false).size();
        
        // Get all surveys (complete set of the GUIDS, most recent (published or unpublished)
        Survey firstSurvey = createSurvey(new SimpleSurvey("First Survey"));
        firstSurvey = versionSurvey(firstSurvey);
        firstSurvey = publishSurvey(TEST_STUDY, firstSurvey); // published is not the most recent
        firstSurvey = versionSurvey(firstSurvey);

        Survey secondSurvey = createSurvey(new SimpleSurvey("Second Survey"));
        secondSurvey = versionSurvey(secondSurvey);
        secondSurvey = publishSurvey(TEST_STUDY, secondSurvey); // published is again not the most recent.
        secondSurvey = versionSurvey(secondSurvey);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // This should include firstVersion and nextVersion.
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentVersion(TEST_STUDY, false);
        
        assertEquals(initialCount + 2, surveys.size());
        assertContainsAllKeys(ImmutableSet.of(new GuidCreatedOnVersionHolderImpl(firstSurvey),
                new GuidCreatedOnVersionHolderImpl(secondSurvey)), surveys);
    }
    
    @Test
    public void canGetAllSurveys() throws Exception {
        Set<GuidCreatedOnVersionHolderImpl> mostRecentVersionSurveys = new HashSet<>();
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true))));

        Survey survey = createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true));

        versionSurvey(survey);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // Get all surveys
        // Make sure this returns all surveys that we created
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentVersion(TEST_STUDY, false);
        assertContainsAllKeys(mostRecentVersionSurveys, surveys);

        // Get all surveys of a version
        surveys = surveyDao.getSurveyAllVersions(TEST_STUDY, survey.getGuid(), false);
        assertEquals("All survey versions are returned", 2, surveys.size());

        Survey version1 = surveys.get(0);
        Survey version2 = surveys.get(1);
        assertEquals("Surveys have same GUID", version1.getGuid(), version2.getGuid());
        assertEquals("Surveys have same Study key", version1.getStudyIdentifier(), version2.getStudyIdentifier());
        assertNotEquals("Surveys have different createdOn attribute", version1.getCreatedOn(), version2.getCreatedOn());
        
        // lets delete an older version and verify that works
        surveyDao.deleteSurveyPermanently(version1);
        assertTrue(surveyDao.getSurveyAllVersions(TEST_STUDY, survey.getGuid(), true).stream()
                .noneMatch((oneSurvey) -> version1.equals(oneSurvey)));
    }

    // GET PUBLISHED SURVEY

    @Test
    public void canRetrieveMostRecentlyPublishedSurveysWithManyVersions() throws Exception {
        // Version 1.
        Survey survey1 = createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true));

        // Version 2.
        Survey survey2 = versionSurvey(survey1);

        // Version 3 (tossed)
        versionSurvey(survey2);

        // Publish one version
        publishSurvey(TEST_STUDY, survey1);
        
        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // Find the survey that we created and make sure it's the published version (survey1)
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false);
        
        boolean foundSurvey1 = false;
        for (Survey oneSurvey : surveys) {
            if (oneSurvey.keysEqual(survey1)) {
                foundSurvey1 = true;
                assertEquals("Retrieved published testSurvey v1", survey1.getCreatedOn(), oneSurvey.getCreatedOn());
            }
        }
        assertTrue(foundSurvey1);

        // Publish a later version
        publishSurvey(TEST_STUDY, survey2);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // Now the most recent version of this testSurvey should be survey2.
        surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false);
        
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
        Survey survey1 = createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true));
        publishSurvey(TEST_STUDY, survey1);

        Survey survey2 = createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true));
        publishSurvey(TEST_STUDY, survey2);

        Survey survey3 = createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true));
        publishSurvey(TEST_STUDY, survey3);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // Make sure this returns all surveys that we created
        List<Survey> published = surveyDao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false);
        assertContainsAllKeys(surveysToDelete, published);
    }

    // DELETE SURVEY

    @Test
    public void canDeleteSurvey() {
        Survey survey = createSurvey(testSurvey);

        surveyDao.deleteSurvey(survey);
        
        // This survey can only be retrieved by direct reference
        Survey retrieved = surveyDao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, survey.getGuid(), true);
        assertNull(retrieved);

        retrieved = surveyDao.getSurveyMostRecentVersion(TEST_STUDY, survey.getGuid());
        assertNull(retrieved);
        
        survey = surveyDao.getSurvey(survey, true);
        assertNotNull(survey);
    }

    @Test
    public void canDeleteSurveyPermanently() {
        Survey survey = createSurvey(testSurvey);

        Survey savedSurvey = surveyDao.createSurvey(survey);
        surveyDao.deleteSurveyPermanently(savedSurvey);
        
        Survey retrieved = surveyDao.getSurvey(survey, true);
        assertNull(retrieved);
    }
    
    @Test
    public void canDeleteThenPermanentlyDeleteSurvey() {
        Survey survey = createSurvey(testSurvey);

        Survey savedSurvey = surveyDao.createSurvey(survey);
        surveyDao.deleteSurvey(savedSurvey);
        
        Survey deletedSurvey = surveyDao.getSurvey(savedSurvey, true);
        assertTrue(deletedSurvey.isDeleted());
        surveyDao.deleteSurveyPermanently(deletedSurvey);
        
        Survey retrievedSurvey = surveyDao.getSurvey(deletedSurvey, true);
        assertNull(retrievedSurvey);
    }
    
    @Test
    public void includeDeletedFlagWorks() throws Exception {
        Survey survey1 = createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true));
        createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true));

        surveyDao.deleteSurvey(survey1);
        noneDeleted(surveyDao.getAllSurveysMostRecentVersion(TEST_STUDY, false), survey1.getGuid());
        anyDeleted(surveyDao.getAllSurveysMostRecentVersion(TEST_STUDY, true), survey1.getGuid());
        
        surveyDao.deleteSurveyPermanently(survey1);
        String guid = survey1.getGuid();
        noneDeleted(surveyDao.getAllSurveysMostRecentVersion(TEST_STUDY, false), guid);
        noneDeleted(surveyDao.getAllSurveysMostRecentVersion(TEST_STUDY, true), guid);
    }
    
    @Test
    public void includeDeletedFlagWorksForPublished() throws Exception {
        Survey survey1 = createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true));
        Survey survey2 = createSurvey(new TestSurvey(DynamoSurveyDaoTest.class, true));
        surveyDao.publishSurvey(TEST_STUDY, survey1, false);
        surveyDao.publishSurvey(TEST_STUDY, survey2, false);

        surveyDao.deleteSurvey(survey1);
        noneDeleted(surveyDao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false), survey1.getGuid());
        anyDeleted(surveyDao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, true), survey1.getGuid());

        surveyDao.deleteSurveyPermanently(survey1);
        noneDeleted(surveyDao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, false), survey1.getGuid());
        noneDeleted(surveyDao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, true), survey1.getGuid());
    }
    
    @Test
    public void rulesMovedToSurveyElementInBackwardsCompatibleManner() throws Exception {
        Survey survey = Survey.create();
        survey.setGuid(UUID.randomUUID().toString());
        survey.setName("Rules test");
        survey.setIdentifier(TestUtils.randomName(DynamoSurveyDaoTest.class));
        survey.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        
        SurveyRule rule = new SurveyRule.Builder().withOperator(Operator.DE).withEndSurvey(true).build();
        
        // This question needs to copy rules from constraints to the element
        SurveyQuestion migrateQuestion = SurveyQuestion.create();
        BooleanConstraints c = new BooleanConstraints();
        c.getRules().add(rule);
        migrateQuestion.setPrompt("Do you have high blood pressure?");
        migrateQuestion.setIdentifier("migrate_question");
        migrateQuestion.setPromptDetail("Be honest: do you have high blood pressue?");
        migrateQuestion.setUiHint(UIHint.CHECKBOX);
        migrateQuestion.setConstraints(c);
        migrateQuestion.setGuid(UUID.randomUUID().toString());
        
        // Information screens can now take rules
        SurveyInfoScreen infoScreen = SurveyInfoScreen.create();
        infoScreen.setPrompt("Do you have high blood pressure?");
        infoScreen.setIdentifier("info_screen");
        infoScreen.setPromptDetail("Be honest: do you have high blood pressue?");
        infoScreen.setGuid(UUID.randomUUID().toString());
        infoScreen.setAfterRules(ImmutableList.of(rule));
        
        // Element rules override anything set in constraints, once they exist.
        SurveyQuestion question = SurveyQuestion.create();
        IntegerConstraints ic = new IntegerConstraints();
        // This rule will be overridden by the rules in the element itself, which now takes precedences
        SurveyRule obsoleteRule = new SurveyRule.Builder().withOperator(Operator.EQ).withValue(10).withEndSurvey(true)
                .build();
        ic.getRules().add(obsoleteRule);
        question.setPrompt("Do you have high blood pressure?");
        question.setIdentifier("migrate_question");
        question.setPromptDetail("Be honest: do you have high blood pressue?");
        question.setUiHint(UIHint.CHECKBOX);
        question.setConstraints(ic);
        question.setGuid(UUID.randomUUID().toString());
        question.setAfterRules(ImmutableList.of(rule));
        
        survey.getElements().add(migrateQuestion);
        survey.getElements().add(infoScreen);
        survey.getElements().add(question);
        
        Survey keys = createSurvey(survey);
        Survey createdSurvey = surveyDao.getSurvey(keys, true);
        
        // Migrated question has been moved up
        SurveyElement element1 = createdSurvey.getElements().get(0);
        assertEquals(rule, element1.getAfterRules().get(0));
        assertEquals(1, element1.getAfterRules().size());
        assertEquals(rule, ((SurveyQuestion)element1).getConstraints().getRules().get(0));
        assertEquals(1, ((SurveyQuestion)element1).getConstraints().getRules().size());
        // Info screen has rule
        assertEquals(rule, createdSurvey.getElements().get(1).getAfterRules().get(0));
        assertEquals(1, createdSurvey.getElements().get(1).getAfterRules().size());
        // Normal question has been moved down, overwriting existing
        assertEquals(rule, createdSurvey.getElements().get(2).getAfterRules().get(0));
        assertEquals(1, createdSurvey.getElements().get(2).getAfterRules().size());
        
        SurveyQuestion savedQuestion = (SurveyQuestion)createdSurvey.getElements().get(2);
        assertEquals(rule, savedQuestion.getAfterRules().get(0));
        assertEquals(1, savedQuestion.getAfterRules().size());
        
        // But if there's a rule on the element and not the constraints, it is not removed. 
        // Constraints are updated.
        Survey survey2 = updateAfterRules(createdSurvey, ImmutableList.of(rule), ImmutableList.of());
        SurveyElement element2 = survey2.getElements().get(0);
        assertEquals(1, element2.getAfterRules().size());
        assertEquals(1, ((SurveyQuestion)element2).getConstraints().getRules().size());
        
        // Setting both to no rules will clear them.
        Survey survey3 = updateAfterRules(survey2, ImmutableList.of(), ImmutableList.of());
        SurveyElement element3 = survey3.getElements().get(0);
        assertEquals(0, element3.getAfterRules().size());
        assertEquals(0, ((SurveyQuestion)element3).getConstraints().getRules().size());
        
        // null rules list in question, non-null non-empty rules list in constraints
        // (this is a variant of the migration case, the rule will be migrated) 
        Survey survey4 = updateAfterRules(survey3, null, ImmutableList.of(rule));
        SurveyElement element4 = survey4.getElements().get(0);
        assertEquals(1, element4.getAfterRules().size());
        assertEquals(1, ((SurveyQuestion)element4).getConstraints().getRules().size());
        
        // empty rules list in question, non-null non-empty rules in constraints
        // (this is a variant of the migration case, the rule will be migrated) 
        Survey survey5 = updateAfterRules(survey4, ImmutableList.of(), ImmutableList.of(rule));
        SurveyElement element5 = survey5.getElements().get(0);
        assertEquals(1, element5.getAfterRules().size());
        assertEquals(1, ((SurveyQuestion)element5).getConstraints().getRules().size());
    }
    
    private Survey updateAfterRules(Survey survey, List<SurveyRule> inElement, List<SurveyRule> inConstraints) {
        SurveyElement element = survey.getElements().get(0);
        element.setAfterRules(inElement);
        ((SurveyQuestion)element).getConstraints().setRules(inConstraints);
        Survey keys = surveyDao.updateSurvey(survey);
        return surveyDao.getSurvey(keys, true);
    }
    
    private static void assertContainsAllKeys(Set<GuidCreatedOnVersionHolderImpl> expected, List<Survey> actual) {
        for (GuidCreatedOnVersionHolder oneExpected : expected) {
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
    
    private void noneDeleted(List<Survey> surveys, String guid) {
        assertTrue("No versions are deleted",
                surveys.stream().filter(survey -> survey.getGuid().equals(guid)).noneMatch(Survey::isDeleted));
    }
    
    private void anyDeleted(List<Survey> surveys, String guid) {
        assertTrue("Some versions are deleted",
                surveys.stream().filter(survey -> survey.getGuid().equals(guid)).anyMatch(Survey::isDeleted));
    }
}
