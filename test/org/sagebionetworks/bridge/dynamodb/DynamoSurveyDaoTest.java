package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.GSI_WAIT_DURATION;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSurveyDaoTest {

    private static Logger logger = LoggerFactory.getLogger(DynamoSurveyDaoTest.class);

    @Resource
    DynamoSurveyDao surveyDao;

    @Resource
    DynamoUploadSchemaDao uploadSchemaDao;

    private final StudyIdentifier studyIdentifier = new StudyIdentifierImpl(TEST_STUDY_IDENTIFIER);
    private TestSurvey testSurvey;
    private Set<GuidCreatedOnVersionHolderImpl> surveysToDelete;

    @BeforeClass
    public static void beforeClass() {
        DynamoInitializer.init(DynamoSurvey.class, DynamoSurveyElement.class);
    }

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
        Survey publishedSurvey = surveyDao.publishSurvey(studyIdentifier, survey);
        return publishedSurvey;
    }

    private class SimpleSurvey extends DynamoSurvey {
        public SimpleSurvey(String name) {
            setName(name);
            setIdentifier("bloodpressure");
            setStudyIdentifier(studyIdentifier.getIdentifier());
        }
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
        createSurvey(testSurvey);
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

        // These fields are updatable.
        survey.setIdentifier("newIdentifier");
        survey.setName("New Name");

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
        Survey updatedSurvey = surveyDao.getSurvey(survey);

        // Verify fields updated.
        assertEquals("Identifier has been changed", "newIdentifier", updatedSurvey.getIdentifier());
        assertEquals("New Name", updatedSurvey.getName());

        // Verify fields not updated.
        assertEquals(TEST_STUDY_IDENTIFIER, updatedSurvey.getStudyIdentifier());
        assertFalse(updatedSurvey.isPublished());

        // Verify modified on updated internally.
        assertNotEquals(originalModifiedOn, updatedSurvey.getModifiedOn());
        assertNull(updatedSurvey.getSchemaRevision());

        surveyDao.deleteSurvey(survey);

        try {
            surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, survey.getGuid());
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException enfe) {
            // expected exception
        }
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
        survey = surveyDao.getSurvey(survey);

        assertEquals("Identifier can be updated", "B", survey.getIdentifier());
        assertEquals("Name can be updated", "C", survey.getName());

        // Now verify the nextVersion has not been changed
        Survey finalVersion = surveyDao.getSurvey(nextVersion);
        assertEquals("Next version has same identifier", nextVersion.getIdentifier(), finalVersion.getIdentifier());
        assertEquals("Next name has not changed", nextVersion.getName(), finalVersion.getName());
    }

    @Test
    public void crudSurveyQuestions() {
        Survey survey = createSurvey(testSurvey);

        int count = survey.getElements().size();
        
        // Now, alter these, and verify they are altered
        survey.getElements().remove(0);
        survey.getElements().get(6).setIdentifier("new gender");
        surveyDao.updateSurvey(survey);

        survey = surveyDao.getSurvey(survey);

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
        Survey survey = createSurvey(testSurvey);

        survey.setVersion(44L);
        surveyDao.updateSurvey(survey);
    }

    @Test(expected = PublishedSurveyException.class)
    public void cannotUpdatePublishedSurveys() {
        Survey survey = createSurvey(testSurvey);
        publishSurvey(studyIdentifier, survey);

        survey.setName("This is a new name");
        surveyDao.updateSurvey(survey);
    }

    // VERSION SURVEY

    @Test
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = createSurvey(testSurvey);

        Survey publishedSurvey = publishSurvey(studyIdentifier, survey);
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
        survey = publishSurvey(studyIdentifier, survey);

        assertTrue("Survey is marked published", survey.isPublished());

        // validate the corresponding schema was created
        UploadSchema uploadSchema = uploadSchemaDao.getUploadSchema(TEST_STUDY_IDENTIFIER, survey.getIdentifier());
        int schemaRev = uploadSchema.getRevision();

        assertEquals(survey.getIdentifier(), uploadSchema.getSchemaId());
        assertEquals(survey.getSchemaRevision().intValue(), uploadSchema.getRevision());
        assertEquals(survey.getName(), uploadSchema.getName());
        assertEquals(UploadSchemaType.IOS_SURVEY, uploadSchema.getSchemaType());

        List<UploadFieldDefinition> fieldDefList = uploadSchema.getFieldDefinitions();
        assertEquals(9, fieldDefList.size());

        assertEquals("high_bp", fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.BOOLEAN, fieldDefList.get(0).getType());

        assertEquals("last_checkup", fieldDefList.get(1).getName());
        assertEquals(UploadFieldType.CALENDAR_DATE, fieldDefList.get(1).getType());

        assertEquals("last_reading", fieldDefList.get(2).getName());
        assertEquals(UploadFieldType.TIMESTAMP, fieldDefList.get(2).getType());

        assertEquals("deleuterium_dosage", fieldDefList.get(3).getName());
        assertEquals(UploadFieldType.FLOAT, fieldDefList.get(3).getType());

        assertEquals("bp_x_day", fieldDefList.get(4).getName());
        assertEquals(UploadFieldType.INT, fieldDefList.get(4).getType());

        assertEquals("time_for_appt", fieldDefList.get(5).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(5).getType());

        assertEquals("deleuterium_x_day", fieldDefList.get(6).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(6).getType());

        assertEquals("feeling", fieldDefList.get(7).getName());
        assertEquals(UploadFieldType.INLINE_JSON_BLOB, fieldDefList.get(7).getType());

        assertEquals("name", fieldDefList.get(8).getName());
        assertEquals(UploadFieldType.ATTACHMENT_BLOB, fieldDefList.get(8).getType());

        // validate get most recently published survey
        Survey pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, survey.getGuid());
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // Publishing again is harmless
        survey = publishSurvey(studyIdentifier, survey);
        pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, survey.getGuid());
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // publishing an already published survey won't bump the schema rev
        assertEquals(schemaRev, pubSurvey.getSchemaRevision().intValue());
    }

    @Test
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = createSurvey(testSurvey);
        survey = publishSurvey(studyIdentifier, survey);

        Survey laterSurvey = versionSurvey(survey);
        assertNotEquals("Surveys do not have the same createdOn", survey.getCreatedOn(),
                laterSurvey.getCreatedOn());

        laterSurvey = publishSurvey(studyIdentifier, laterSurvey);

        Survey pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, survey.getGuid());
        
        assertEquals("Later testSurvey is the published testSurvey", laterSurvey.getCreatedOn(), pubSurvey.getCreatedOn());
    }

    // GET SURVEYS
    
    @Test
    public void failToGetSurveysByBadStudyKey() {
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentVersion(new StudyIdentifierImpl("foo"));
        assertEquals("No surveys", 0, surveys.size());
    }

    @Test
    public void getSurveyAllVersions() {
        // Get a survey (one GUID), and no other surveys, all the versions, ordered most to least recent
        createSurvey(new SimpleSurvey("First Survey")); // spurious survey
        
        Survey versionedSurvey = createSurvey(new SimpleSurvey("Second Survey"));
        versionSurvey(versionedSurvey);
        versionSurvey(versionedSurvey);
        
        Survey finalVersion = versionSurvey(versionedSurvey);
        
        long lastCreatedOnTime = finalVersion.getCreatedOn();
        
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
        
        Survey firstVersion = createSurvey(new SimpleSurvey("First Survey"));
        Survey middleVersion = versionSurvey(firstVersion);
        Survey finalVersion = versionSurvey(firstVersion);

        // Now confuse the matter by publishing a version before the last one.
        publishSurvey(studyIdentifier, middleVersion);

        Survey result = surveyDao.getSurveyMostRecentVersion(studyIdentifier, firstVersion.getGuid());
        assertEquals("Retrieves most recent version", finalVersion.getCreatedOn(), result.getCreatedOn());
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersion() {
        // Get one survey (with the GUID), the most recently published version
        
        Survey firstVersion = createSurvey(new SimpleSurvey("First Survey"));
        Survey middleVersion = versionSurvey(firstVersion);
        versionSurvey(firstVersion);
        
        // This is the version we want to retrieve now
        publishSurvey(studyIdentifier, middleVersion);

        Survey result = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, firstVersion.getGuid());
        assertEquals("Retrieves most recent version", middleVersion.getCreatedOn(), result.getCreatedOn());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getSurveyMostRecentlyPublishedVersionThrowsException() {
        Survey firstVersion = createSurvey(new SimpleSurvey("First Survey"));
        surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, firstVersion.getGuid());
    }
    
    @Test
    public void getAllSurveysMostRecentlyPublishedVersion() throws Exception {
        // Get all surveys (complete set of the GUIDS, most recently published (if never published, GUID isn't included)
        Survey firstSurvey = createSurvey(new SimpleSurvey("First Survey"));
        firstSurvey = versionSurvey(firstSurvey);
        firstSurvey = versionSurvey(firstSurvey);
        firstSurvey = publishSurvey(studyIdentifier, firstSurvey);

        Survey secondSurvey = createSurvey(new SimpleSurvey("Second Survey"));
        secondSurvey = versionSurvey(secondSurvey);
        secondSurvey = versionSurvey(secondSurvey);
        secondSurvey = publishSurvey(studyIdentifier, secondSurvey);
        
        // This version is later, and should be returned
        secondSurvey = versionSurvey(secondSurvey);
        secondSurvey = publishSurvey(studyIdentifier, secondSurvey);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // This should include firstVersion and nextVersion.
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);

        assertEquals(2, surveys.size());
        assertEquals(Sets.newHashSet(surveys), Sets.newHashSet(firstSurvey, secondSurvey));
    }
    
    @Test
    public void getAllSurveysMostRecentVersion() throws Exception {
        // Get all surveys (complete set of the GUIDS, most recent (published or unpublished)
        Survey firstSurvey = createSurvey(new SimpleSurvey("First Survey"));
        firstSurvey = versionSurvey(firstSurvey);
        firstSurvey = publishSurvey(studyIdentifier, firstSurvey); // published is not the most recent
        firstSurvey = versionSurvey(firstSurvey);

        Survey secondSurvey = createSurvey(new SimpleSurvey("Second Survey"));
        secondSurvey = versionSurvey(secondSurvey);
        secondSurvey = publishSurvey(studyIdentifier, secondSurvey); // published is again not the most recent.
        secondSurvey = versionSurvey(secondSurvey);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // This should include firstVersion and nextVersion.
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentVersion(studyIdentifier);
        
        assertEquals(2, surveys.size());
        assertEquals(Sets.newHashSet(surveys), Sets.newHashSet(firstSurvey, secondSurvey));
    }

    @Test
    public void canGetSummarySurvey() throws Exception {
        // Add an information screen so we can verify it is removed.
        DynamoSurveyInfoScreen info = new DynamoSurveyInfoScreen();
        info.setIdentifier("info");
        info.setPrompt("This is the prompt.");
        info.setPromptDetail("This is the prompt detail.");
        info.setTitle("This is a title.");
        
        // Create and publish a couple of surveys
        Survey survey = new TestSurvey(true);
        survey.getElements().add(info);
        survey = createSurvey(survey);
        survey = publishSurvey(studyIdentifier, survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        
        survey = new TestSurvey(true);
        survey.getElements().add(info);
        survey = createSurvey(survey);
        survey = publishSurvey(studyIdentifier, survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        
        List<Survey> surveys = surveyDao.getSurveysSummary(studyIdentifier);
        assertTrue(surveys.size() >= 2);
        for (Survey aSurvey : surveys) {
            assertTrue(doesNotContainSurveyInfoScreen(aSurvey));    
        }
    }
    
    @Test
    public void canGetAllSurveys() throws Exception {
        Set<GuidCreatedOnVersionHolderImpl> mostRecentVersionSurveys = new HashSet<>();
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(createSurvey(new TestSurvey(true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(createSurvey(new TestSurvey(true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(createSurvey(new TestSurvey(true))));
        mostRecentVersionSurveys.add(new GuidCreatedOnVersionHolderImpl(createSurvey(new TestSurvey(true))));

        Survey survey = createSurvey(new TestSurvey(true));

        versionSurvey(survey);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // Get all surveys
        // Make sure this returns all surveys that we created
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentVersion(studyIdentifier);
        assertContainsAllKeys(mostRecentVersionSurveys, surveys);

        // Get all surveys of a version
        surveys = surveyDao.getSurveyAllVersions(studyIdentifier, survey.getGuid());
        assertEquals("All survey versions are returned", 2, surveys.size());

        Survey version1 = surveys.get(0);
        Survey version2 = surveys.get(1);
        assertEquals("Surveys have same GUID", version1.getGuid(), version2.getGuid());
        assertEquals("Surveys have same Study key", version1.getStudyIdentifier(), version2.getStudyIdentifier());
        assertNotEquals("Surveys have different createdOn attribute", version1.getCreatedOn(), version2.getCreatedOn());
    }

    // GET PUBLISHED SURVEY

    @Test
    public void canRetrieveMostRecentlyPublishedSurveysWithManyVersions() throws Exception {
        // Version 1.
        Survey survey1 = createSurvey(new TestSurvey(true));

        // Version 2.
        Survey survey2 = versionSurvey(survey1);

        // Version 3 (tossed)
        versionSurvey(survey2);

        // Publish one version
        publishSurvey(studyIdentifier, survey1);
        
        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // Find the survey that we created and make sure it's the published version (survey1)
        List<Survey> surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
        
        boolean foundSurvey1 = false;
        for (Survey oneSurvey : surveys) {
            if (oneSurvey.keysEqual(survey1)) {
                foundSurvey1 = true;
                assertEquals("Retrieved published testSurvey v1", survey1.getCreatedOn(), oneSurvey.getCreatedOn());
            }
        }
        assertTrue(foundSurvey1);

        // Publish a later version
        publishSurvey(studyIdentifier, survey2);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // Now the most recent version of this testSurvey should be survey2.
        surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
        
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
        Survey survey1 = createSurvey(new TestSurvey(true));
        publishSurvey(studyIdentifier, survey1);

        Survey survey2 = createSurvey(new TestSurvey(true));
        publishSurvey(studyIdentifier, survey2);

        Survey survey3 = createSurvey(new TestSurvey(true));
        publishSurvey(studyIdentifier, survey3);

        // We must wait here because the above getter uses a secondary global index, and consistent
        // reads are not supported on such indices.
        Thread.sleep(GSI_WAIT_DURATION);
        // Make sure this returns all surveys that we created
        List<Survey> published = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
        assertContainsAllKeys(surveysToDelete, published);
    }

    // DELETE SURVEY

    @Test
    public void canDeleteSurvey() {
        Survey survey = createSurvey(testSurvey);

        surveyDao.deleteSurvey(survey);
        
        // This survey can only be retrieved by direct reference
        try {
            surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, survey.getGuid());
            fail("Should have thrown exception [1].");
        } catch(EntityNotFoundException e) {
            assertEquals("Survey not found.", e.getMessage());
        }
        try {
            surveyDao.getSurveyMostRecentVersion(studyIdentifier, survey.getGuid());
            fail("Should have thrown exception [3].");
        } catch(EntityNotFoundException e) {
            assertEquals("Survey not found.", e.getMessage());
        }
        survey = surveyDao.getSurvey(survey);
        assertNotNull(survey);
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
    
    private boolean doesNotContainSurveyInfoScreen(Survey survey) {
        for (SurveyElement element : survey.getElements()) {
            if (element instanceof SurveyInfoScreen) {
                return false;
            }
        }
        return true;
    }
}
