package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;

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

    @Resource
    DynamoUploadSchemaDao uploadSchemaDao;

    private final StudyIdentifier studyIdentifier = new StudyIdentifierImpl(TEST_STUDY_IDENTIFIER);

    private TestSurvey testSurvey;
    private Set<GuidCreatedOnVersionHolderImpl> surveysToDelete;
    private Set<String> schemaIdsToDelete;

    @BeforeClass
    public static void beforeClass() {
        DynamoInitializer.init(DynamoSurvey.class, DynamoSurveyElement.class);
    }

    @Before
    public void before() {
        testSurvey = new TestSurvey(true);
        surveysToDelete = new HashSet<>();
        schemaIdsToDelete = new HashSet<>();
    }

    @After
    public void after() {
        // clean up surveys
        for (GuidCreatedOnVersionHolder oneSurvey : surveysToDelete) {
            try {
                // close (unpublish) survey before deleting it, since published surveys can't be deleted
                surveyDao.closeSurvey(oneSurvey);
                surveyDao.deleteSurvey(studyIdentifier, oneSurvey);
            } catch (Exception ex) {
                // suppress exception
            }
        }

        // clean up schemas
        for (String oneSchemaId : schemaIdsToDelete) {
            try {
                uploadSchemaDao.deleteUploadSchemaById(studyIdentifier, oneSchemaId);
            } catch (Exception ex) {
                // suppress exception
            }
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
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        surveyDao.createSurvey(testSurvey);
    }

    @Test
    public void crudSurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);

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

        surveyDao.deleteSurvey(studyIdentifier, survey);

        try {
            surveyDao.getSurvey(survey);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException enfe) {
            // expected exception
        }
    }

    // UPDATE SURVEY

    @Test
    public void canUpdateASurveyVersion() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        Survey nextVersion = surveyDao.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(nextVersion));

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
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

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
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        survey.setVersion(44L);
        surveyDao.updateSurvey(survey);
    }

    @Test(expected = PublishedSurveyException.class)
    public void cannotUpdatePublishedSurveys() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        surveyDao.publishSurvey(studyIdentifier, survey);
        schemaIdsToDelete.add(survey.getIdentifier());

        survey.setName("This is a new name");
        surveyDao.updateSurvey(survey);
    }

    // VERSION SURVEY

    @Test
    public void canVersionASurveyEvenIfPublished() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        Survey publishedSurvey = surveyDao.publishSurvey(studyIdentifier, survey);
        schemaIdsToDelete.add(survey.getIdentifier());
        assertNotNull(publishedSurvey.getSchemaRevision());

        Long originalVersion = survey.getCreatedOn();
        survey = surveyDao.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        assertEquals("Newly versioned testSurvey is not published", false, survey.isPublished());
        assertNull(survey.getSchemaRevision());

        Long newVersion = survey.getCreatedOn();
        assertNotEquals("Versions differ", newVersion, originalVersion);
    }

    @Test
    public void versioningASurveyCopiesTheQuestions() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        String v1SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v1Guid = survey.getElements().get(0).getGuid();

        survey = surveyDao.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        String v2SurveyCompoundKey = survey.getElements().get(0).getSurveyCompoundKey();
        String v2Guid = survey.getElements().get(0).getGuid();

        assertNotEquals("Survey reference differs", v1SurveyCompoundKey, v2SurveyCompoundKey);
        assertNotEquals("Survey question GUID differs", v1Guid, v2Guid);
    }

    // PUBLISH SURVEY

    @Test
    public void canPublishASurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        survey = surveyDao.publishSurvey(studyIdentifier, survey);
        schemaIdsToDelete.add(survey.getIdentifier());

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
        survey = surveyDao.publishSurvey(studyIdentifier, survey);
        pubSurvey = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, survey.getGuid());
        assertEquals("Same testSurvey GUID", survey.getGuid(), pubSurvey.getGuid());
        assertEquals("Same testSurvey createdOn", survey.getCreatedOn(), pubSurvey.getCreatedOn());
        assertTrue("Published testSurvey is marked published", pubSurvey.isPublished());

        // publishing an already published survey won't bump the schema rev
        assertEquals(schemaRev, pubSurvey.getSchemaRevision().intValue());
    }

    @Test
    public void canPublishANewerVersionOfASurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        survey = surveyDao.publishSurvey(studyIdentifier, survey);
        schemaIdsToDelete.add(survey.getIdentifier());

        Survey laterSurvey = surveyDao.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(laterSurvey));
        assertNotEquals("Surveys do not have the same createdOn", survey.getCreatedOn(),
                laterSurvey.getCreatedOn());

        laterSurvey = surveyDao.publishSurvey(studyIdentifier, laterSurvey);

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
        Survey spuriousSurvey = surveyDao.createSurvey(new SimpleSurvey()); // spurious survey
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(spuriousSurvey));
        
        Survey versionedSurvey = surveyDao.createSurvey(new SimpleSurvey());
        surveyDao.versionSurvey(versionedSurvey);
        surveyDao.versionSurvey(versionedSurvey);
        long lastCreatedOnTime = surveyDao.versionSurvey(versionedSurvey).getCreatedOn();
        
        List<Survey> surveyVersions = surveyDao.getSurveyAllVersions(studyIdentifier, versionedSurvey.getGuid());
        for (Survey oneSurveyVersion : surveyVersions) {
            surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(oneSurveyVersion));
        }

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
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(firstVersion));
        Survey middleVersion = surveyDao.versionSurvey(firstVersion);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(middleVersion));
        Survey finalVersion = surveyDao.versionSurvey(firstVersion);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(finalVersion));

        // Now confuse the matter by publishing a version before the last one.
        surveyDao.publishSurvey(studyIdentifier, middleVersion);
        schemaIdsToDelete.add(middleVersion.getIdentifier());

        Survey result = surveyDao.getSurveyMostRecentVersion(studyIdentifier, firstVersion.getGuid());
        assertEquals("Retrieves most recent version", finalVersion.getCreatedOn(), result.getCreatedOn());
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersion() {
        // Get one survey (with the GUID), the most recently published version
        
        Survey firstVersion = surveyDao.createSurvey(new SimpleSurvey());
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(firstVersion));
        Survey middleVersion = surveyDao.versionSurvey(firstVersion);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(middleVersion));
        Survey finalVersion = surveyDao.versionSurvey(firstVersion);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(finalVersion));
        
        // This is the version we want to retrieve now
        surveyDao.publishSurvey(studyIdentifier, middleVersion);
        schemaIdsToDelete.add(middleVersion.getIdentifier());

        Survey result = surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, firstVersion.getGuid());
        assertEquals("Retrieves most recent version", middleVersion.getCreatedOn(), result.getCreatedOn());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getSurveyMostRecentlyPublishedVersionThrowsException() {
        Survey firstVersion = surveyDao.createSurvey(new SimpleSurvey());
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(firstVersion));
        surveyDao.getSurveyMostRecentlyPublishedVersion(studyIdentifier, firstVersion.getGuid());
    }
    
    @Test
    public void getAllSurveysMostRecentlyPublishedVersion() {
        // Get all surveys (complete set of the GUIDS, most recently published (if never published, GUID isn't included)
        Survey firstVersion = surveyDao.createSurvey(new SimpleSurvey());
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(firstVersion));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyDao.versionSurvey(firstVersion)));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyDao.versionSurvey(firstVersion)));
        surveyDao.publishSurvey(studyIdentifier, firstVersion);
        schemaIdsToDelete.add(firstVersion.getIdentifier());

        Survey nextVersion = surveyDao.createSurvey(new SimpleSurvey());
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(nextVersion));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyDao.versionSurvey(nextVersion)));
        nextVersion = surveyDao.versionSurvey(nextVersion);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(nextVersion));
        surveyDao.publishSurvey(studyIdentifier, nextVersion);
        
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
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(firstVersion));
        firstVersion = surveyDao.versionSurvey(firstVersion);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(firstVersion));
        surveyDao.publishSurvey(studyIdentifier, firstVersion); // published is not the most recent
        schemaIdsToDelete.add(firstVersion.getIdentifier());
        firstVersion = surveyDao.versionSurvey(firstVersion);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(firstVersion));

        Survey nextVersion = surveyDao.createSurvey(new SimpleSurvey());
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(nextVersion));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyDao.versionSurvey(nextVersion)));
        nextVersion = surveyDao.versionSurvey(nextVersion);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(nextVersion));
        surveyDao.publishSurvey(studyIdentifier, nextVersion); // published is again not the most recent.
        nextVersion = surveyDao.versionSurvey(nextVersion);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(nextVersion));

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
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyDao.createSurvey(new TestSurvey(true))));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyDao.createSurvey(new TestSurvey(true))));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyDao.createSurvey(new TestSurvey(true))));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(surveyDao.createSurvey(new TestSurvey(true))));

        Survey survey = surveyDao.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        Survey nextVersion = surveyDao.versionSurvey(survey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(nextVersion));

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
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));

        survey = surveyDao.publishSurvey(studyIdentifier, survey);
        schemaIdsToDelete.add(survey.getIdentifier());
        assertNotNull(survey.getSchemaRevision());

        survey = surveyDao.closeSurvey(survey);
        assertEquals("Survey no longer published", false, survey.isPublished());
        assertNull(survey.getSchemaRevision());

        survey = surveyDao.getSurvey(survey);
        assertEquals("Survey no longer published", false, survey.isPublished());
        assertNull(survey.getSchemaRevision());
    }

    // GET PUBLISHED SURVEY

    @Test
    public void canRetrieveMostRecentlyPublishedSurveysWithManyVersions() {
        // Version 1.
        Survey survey1 = surveyDao.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey1));

        // Version 2.
        Survey survey2 = surveyDao.versionSurvey(survey1);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey2));

        // Version 3 (tossed)
        Survey survey3 = surveyDao.versionSurvey(survey2);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey3));

        // Publish one version
        surveyDao.publishSurvey(studyIdentifier, survey1);
        schemaIdsToDelete.add(survey1.getIdentifier());

        List<Survey> surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
        assertEquals("Retrieved published testSurvey v1", survey1.getCreatedOn(), surveys.get(0).getCreatedOn());

        // Publish a later version
        surveyDao.publishSurvey(studyIdentifier, survey2);

        // Now the most recent version of this testSurvey should be survey2.
        surveys = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);
        assertEquals("Retrieved published testSurvey v2", survey2.getCreatedOn(), surveys.get(0).getCreatedOn());
    }

    @Test
    public void canRetrieveMostRecentPublishedSurveysWithManySurveys() {
        Survey survey1 = surveyDao.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey1));
        surveyDao.publishSurvey(studyIdentifier, survey1);
        schemaIdsToDelete.add(survey1.getIdentifier());

        Survey survey2 = surveyDao.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey2));
        surveyDao.publishSurvey(studyIdentifier, survey2);

        Survey survey3 = surveyDao.createSurvey(new TestSurvey(true));
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey3));
        surveyDao.publishSurvey(studyIdentifier, survey3);

        List<Survey> published = surveyDao.getAllSurveysMostRecentlyPublishedVersion(studyIdentifier);

        assertEquals("There are three published surveys", 3, published.size());
        assertEquals("The first is survey3", survey3.getGuid(), published.get(0).getGuid());
        assertEquals("The middle is survey2", survey2.getGuid(), published.get(1).getGuid());
        assertEquals("The last is survey1", survey1.getGuid(), published.get(2).getGuid());
    }

    // DELETE SURVEY

    @Test
    public void cannotDeletePublishedSurvey() {
        Survey survey = surveyDao.createSurvey(testSurvey);
        surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
        survey = surveyDao.publishSurvey(studyIdentifier, survey);
        schemaIdsToDelete.add(survey.getIdentifier());
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
            surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
            survey = surveyDao.publishSurvey(studyIdentifier, survey);
            schemaIdsToDelete.add(survey.getIdentifier());

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
            surveysToDelete.add(new GuidCreatedOnVersionHolderImpl(survey));
            survey = surveyDao.publishSurvey(studyIdentifier, survey);
            schemaIdsToDelete.add(survey.getIdentifier());

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
