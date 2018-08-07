package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

@RunWith(MockitoJUnitRunner.class)
public class DynamoSurveyDaoMockTest {
    private static final DateTime MOCK_NOW = DateTime.parse("2016-08-24T15:23:57.123-0700");
    private static final long MOCK_NOW_MILLIS = MOCK_NOW.getMillis();

    private static final int SCHEMA_REV = 42;

    private static final String SURVEY_GUID = "test-guid";
    private static final long SURVEY_CREATED_ON = 1337;
    private static final String SURVEY_ID = "test-survey";
    private static final GuidCreatedOnVersionHolder SURVEY_KEY = new GuidCreatedOnVersionHolderImpl(SURVEY_GUID,
            SURVEY_CREATED_ON);

    private Survey survey;
    
    @Spy
    private DynamoSurveyDao surveyDao;

    @Mock
    private DynamoDBMapper mockSurveyMapper;

    @Mock
    private DynamoDBMapper mockSurveyElementMapper;
    
    @Mock
    private UploadSchemaService mockSchemaService;
    
    @Mock
    private QueryResultPage<Survey> mockQueryResultPage;
    
    @Mock
    private QueryResultPage<SurveyElement> mockElementQueryResultPage;
    
    @Captor
    private ArgumentCaptor<Survey> surveyCaptor;
    
    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Before
    public void setup() {
        // set up survey
        survey = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        survey.setIdentifier(SURVEY_ID);
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);

        // mock schema dao
        UploadSchema schema = UploadSchema.create();
        schema.setRevision(SCHEMA_REV);

        when(mockSchemaService.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, true)).thenReturn(
                schema);

        // set up survey dao for test
        surveyDao.setSurveyMapper(mockSurveyMapper);
        surveyDao.setUploadSchemaService(mockSchemaService);
        surveyDao.setSurveyElementMapper(mockSurveyElementMapper);

        // spy getSurvey() - There's a lot of complex logic in that query builder that's irrelevant to what we're
        // trying to test. Rather than over-specify our test and make our tests overly complicated, we'll just spy out
        // getSurvey().
        doReturn(survey).when(surveyDao).getSurvey(eq(SURVEY_KEY), anyBoolean());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void updateSurveyFailsOnDeletedSurvey() {
        DynamoSurvey existing = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        existing.setDeleted(true);
        
        List<Survey> results = Lists.newArrayList(existing);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        survey.setDeleted(true);
        surveyDao.updateSurvey(survey);
    }
    
    @Test
    public void updateSurveySucceedsOnUndeletedSurvey() {
        DynamoSurvey existing = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        existing.setDeleted(true);
        
        List<Survey> results = Lists.newArrayList(existing);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        survey.setDeleted(false);
        survey.setName("New title");
        Survey updatedSurvey = surveyDao.updateSurvey(survey);
        
        assertEquals("New title", updatedSurvey.getName());
    }
    
    @Test
    public void publishSurvey() {
        // populate the survey with at least one question
        SurveyQuestion surveyQuestion = new DynamoSurveyQuestion();
        surveyQuestion.setIdentifier("int");
        surveyQuestion.setConstraints(new IntegerConstraints());

        survey.setElements(ImmutableList.of(surveyQuestion));

        // execute and validate
        Survey retval = surveyDao.publishSurvey(TestConstants.TEST_STUDY, survey, true);
        assertTrue(retval.isPublished());
        assertEquals(MOCK_NOW_MILLIS, retval.getModifiedOn());
        assertEquals(SCHEMA_REV, retval.getSchemaRevision().intValue());

        verify(mockSurveyMapper).save(same(retval));
    }

    @Test
    public void publishSurveyWithInfoScreensOnly() {
        // populate the survey with an info screen and no questions
        SurveyInfoScreen infoScreen = new DynamoSurveyInfoScreen();
        infoScreen.setIdentifier("test-info-screen");
        infoScreen.setTitle("Test Info Screen");
        infoScreen.setPrompt("This info screen doesn't do anything, other than not being a question.");

        survey.setElements(ImmutableList.of(infoScreen));

        // same test as above, except we *don't* call through to the upload schema DAO
        Survey retval = surveyDao.publishSurvey(TestConstants.TEST_STUDY, survey, true);
        assertTrue(retval.isPublished());
        assertEquals(MOCK_NOW_MILLIS, retval.getModifiedOn());
        assertNull(retval.getSchemaRevision());

        verify(mockSurveyMapper).save(same(retval));
        verify(mockSchemaService, never()).createUploadSchemaFromSurvey(any(), any(), anyBoolean());
    }

    @Test
    public void deleteSurveyAlsoDeletesSchema() {
        // We also need to spy deleteAllElements(), because there's also a lot of complex logic there.
        doNothing().when(surveyDao).deleteAllElements(SURVEY_GUID, SURVEY_CREATED_ON);
        
        List<Survey> results = Lists.newArrayList(survey);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        // Execute
        surveyDao.deleteSurveyPermanently(SURVEY_KEY);

        // Validate backends
        verify(surveyDao).deleteAllElements(SURVEY_GUID, SURVEY_CREATED_ON);
        verify(mockSurveyMapper).delete(survey);
        verify(mockSchemaService).deleteUploadSchemaById(TestConstants.TEST_STUDY, SURVEY_ID);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteSurveyPermanentlyNoSurvey() {
        List<Survey> results = Lists.newArrayList();
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("keys", DateTime.now().getMillis());
        surveyDao.deleteSurveyPermanently(keys);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void updateSurveyExistingDeletedNotFound() {
        DynamoSurvey existing = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        existing.setIdentifier(SURVEY_ID);
        existing.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        existing.setDeleted(true);
        existing.setPublished(false);
        
        List<Survey> results = Lists.newArrayList(existing);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        // This is not an undelete, it fails with a not found exception
        survey.setDeleted(true);
        surveyDao.updateSurvey(survey);
    }
    
    @Test
    public void updateSurveyUndeleteExistingDeletedOK() {
        DynamoSurvey existing = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        existing.setIdentifier(SURVEY_ID);
        existing.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        existing.setDeleted(true);
        existing.setPublished(false);
        
        List<Survey> results = Lists.newArrayList(existing);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        survey.setDeleted(false);
        survey.getElements().add(SurveyInfoScreen.create());
        surveyDao.updateSurvey(survey);

        verify(mockSurveyMapper).save(surveyCaptor.capture());
        assertFalse(surveyCaptor.getValue().isDeleted());
        assertEquals(1, surveyCaptor.getValue().getElements().size());
    }
    
    @Test(expected = PublishedSurveyException.class)
    public void updateSurveyAlreadyPublishedThrowsException() {
        DynamoSurvey existing = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        existing.setIdentifier(SURVEY_ID);
        existing.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        existing.setDeleted(false);
        existing.setPublished(true);
        
        List<Survey> results = Lists.newArrayList(existing);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        // Not undeleting... should throw exception
        survey.setDeleted(false);
        survey.getElements().add(SurveyInfoScreen.create());
        surveyDao.updateSurvey(survey);
    }
    
    @Test
    public void updateSurveyUndeletePublishedOK() {
        DynamoSurvey existing = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        existing.setIdentifier(SURVEY_ID);
        existing.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        existing.setDeleted(true);
        existing.setPublished(true);
        
        List<Survey> results = Lists.newArrayList(existing);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        // Verify that we load the elements for the update
        List<SurveyElement> elementResults = Lists.newArrayList(SurveyInfoScreen.create());
        doReturn(elementResults).when(mockElementQueryResultPage).getResults();
        doReturn(mockElementQueryResultPage).when(mockSurveyElementMapper).queryPage(eq(DynamoSurveyElement.class), any());
        
        survey.setDeleted(false);
        surveyDao.updateSurvey(survey);

        verify(mockSurveyMapper).save(surveyCaptor.capture());
        assertFalse(surveyCaptor.getValue().isDeleted());
        assertEquals(1, surveyCaptor.getValue().getElements().size());
    }
}
