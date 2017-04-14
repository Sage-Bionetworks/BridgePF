package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class DynamoSurveyDaoMockTest {
    private static final DateTime MOCK_NOW = DateTime.parse("2016-08-24T15:23:57.123-0700");
    private static final long MOCK_NOW_MILLIS = MOCK_NOW.getMillis();

    private static final int SCHEMA_REV = 42;

    private static final String SURVEY_GUID = "test-guid";
    private static final long SURVEY_CREATED_ON = 1337;
    private static final String SURVEY_ID = "test-survey";
    private static final GuidCreatedOnVersionHolder SURVEY_KEY = new GuidCreatedOnVersionHolderImpl(SURVEY_GUID,
            SURVEY_CREATED_ON);

    private UploadSchemaService mockSchemaService;
    private DynamoDBMapper mockSurveyMapper;
    private Survey survey;
    private DynamoSurveyDao surveyDao;

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

        // mock mapper
        mockSurveyMapper = mock(DynamoDBMapper.class);

        // mock schema dao
        UploadSchema schema = UploadSchema.create();
        schema.setRevision(SCHEMA_REV);

        mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, true)).thenReturn(
                schema);

        // set up survey dao for test
        surveyDao = spy(new DynamoSurveyDao());
        surveyDao.setSurveyMapper(mockSurveyMapper);
        surveyDao.setUploadSchemaService(mockSchemaService);

        // spy getSurvey() - There's a lot of complex logic in that query builder that's irrelevant to what we're
        // trying to test. Rather than over-specify our test and make our tests overly complicated, we'll just spy out
        // getSurvey().
        doReturn(survey).when(surveyDao).getSurvey(SURVEY_KEY);
    }

    @Test
    public void publishSurvey() {
        // populate the survey with at least one question
        SurveyQuestion surveyQuestion = new DynamoSurveyQuestion();
        surveyQuestion.setIdentifier("int");
        surveyQuestion.setConstraints(new IntegerConstraints());

        survey.setElements(ImmutableList.of(surveyQuestion));

        // execute and validate
        Survey retval = surveyDao.publishSurvey(TestConstants.TEST_STUDY, SURVEY_KEY, true);
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
        Survey retval = surveyDao.publishSurvey(TestConstants.TEST_STUDY, SURVEY_KEY, true);
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

        // Execute
        surveyDao.deleteSurveyPermanently(SURVEY_KEY);

        // Validate backends
        verify(surveyDao).deleteAllElements(SURVEY_GUID, SURVEY_CREATED_ON);
        verify(mockSurveyMapper).delete(survey);
        verify(mockSchemaService).deleteUploadSchemaById(TestConstants.TEST_STUDY, SURVEY_ID);
    }
}
