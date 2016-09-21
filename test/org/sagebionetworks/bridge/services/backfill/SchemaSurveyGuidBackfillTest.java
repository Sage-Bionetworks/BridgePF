package org.sagebionetworks.bridge.services.backfill;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

@SuppressWarnings("unchecked")
public class SchemaSurveyGuidBackfillTest {
    private static final BackfillCallback MOCK_CALLBACK = mock(BackfillCallback.class);
    private static final BackfillTask MOCK_TASK = mock(BackfillTask.class);

    private static final Study TEST_STUDY;
    static {
        TEST_STUDY = new DynamoStudy();
        TEST_STUDY.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
    }

    private static final long TEST_SURVEY_CREATED_ON = 1111;
    private static final String TEST_SURVEY_GUID = "test-guid";
    private static final String TEST_SURVEY_ID = "test-survey";
    private static final int TEST_SURVEY_SCHEMA_REV = 2;
    private static final Survey TEST_SURVEY;
    static {
        TEST_SURVEY = new DynamoSurvey(TEST_SURVEY_GUID, TEST_SURVEY_CREATED_ON);
        TEST_SURVEY.setIdentifier(TEST_SURVEY_ID);
        TEST_SURVEY.setSchemaRevision(TEST_SURVEY_SCHEMA_REV);
    }

    private SchemaSurveyGuidBackfill backfill;
    private StudyService studyService;
    private SurveyService surveyService;
    private UploadSchemaService uploadSchemaService;

    @Before
    public void setup() {
        // Mock dependencies. Behavior will be set up in individual tests.
        studyService = mock(StudyService.class);
        surveyService = mock(SurveyService.class);
        uploadSchemaService = mock(UploadSchemaService.class);

        // Set up backfill. Mock recordMessage() and recordError(), so the superclass doesn't try to do anything weird.
        // Also, mock out sleep() to reduce needless delay in unit tests.
        backfill = spy(new SchemaSurveyGuidBackfill());
        doNothing().when(backfill).recordMessage(any(), any(), any());
        doNothing().when(backfill).recordError(any(), any(), any(), any());
        doNothing().when(backfill).sleep();

        backfill.setStudyService(studyService);
        backfill.setSurveyService(surveyService);
        backfill.setUploadSchemaService(uploadSchemaService);
    }

    @Test
    public void happyCase() {
        // 2 studies, 2 surveys each

        // create studies - All we need is the ID.
        Study study1 = new DynamoStudy();
        study1.setIdentifier("study-1");
        StudyIdentifier study1Id = new StudyIdentifierImpl("study-1");

        Study study2 = new DynamoStudy();
        study2.setIdentifier("study-2");
        StudyIdentifier study2Id = new StudyIdentifierImpl("study-2");

        when(studyService.getStudies()).thenReturn(ImmutableList.of(study1, study2));

        // create surveys - All we need are guid, createdOn, identifier, and schemaRev
        Survey survey1a = new DynamoSurvey("guid-1a", 0x1a);
        survey1a.setIdentifier("survey-1a");
        survey1a.setSchemaRevision(11);

        Survey survey1b = new DynamoSurvey("guid-1b", 0x1b);
        survey1b.setIdentifier("survey-1b");
        survey1b.setSchemaRevision(12);

        when(surveyService.getAllSurveysMostRecentlyPublishedVersion(study1Id)).thenReturn(ImmutableList.of(survey1a,
                survey1b));

        Survey survey2a = new DynamoSurvey("guid-2a", 0x2a);
        survey2a.setIdentifier("survey-2a");
        survey2a.setSchemaRevision(21);

        Survey survey2b = new DynamoSurvey("guid-2b", 0x2b);
        survey2b.setIdentifier("survey-2b");
        survey2b.setSchemaRevision(22);

        when(surveyService.getAllSurveysMostRecentlyPublishedVersion(study2Id)).thenReturn(ImmutableList.of(survey2a,
                survey2b));

        // Mock schema service. Since everything is mocked, we don't need to worry about parameters other than survey
        // guid and createdOn, which are all blank in this case.
        when(uploadSchemaService.getUploadSchemaByIdAndRev(any(), any(), anyInt())).thenAnswer(
                invocation -> new DynamoUploadSchema());

        // execute
        backfill.doBackfill(MOCK_TASK, MOCK_CALLBACK);

        // verify calls to get schema - All we care is that the survey guid and createdOn were set in the updated
        // schemas.
        ArgumentCaptor<UploadSchema> schema1aCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(uploadSchemaService).updateSchemaRevisionV4(eq(study1Id), eq("survey-1a"), eq(11),
                schema1aCaptor.capture());
        UploadSchema schema1a = schema1aCaptor.getValue();
        assertEquals("guid-1a", schema1a.getSurveyGuid());
        assertEquals(0x1a, schema1a.getSurveyCreatedOn().longValue());

        ArgumentCaptor<UploadSchema> schema1bCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(uploadSchemaService).updateSchemaRevisionV4(eq(study1Id), eq("survey-1b"), eq(12),
                schema1bCaptor.capture());
        UploadSchema schema1b = schema1bCaptor.getValue();
        assertEquals("guid-1b", schema1b.getSurveyGuid());
        assertEquals(0x1b, schema1b.getSurveyCreatedOn().longValue());

        ArgumentCaptor<UploadSchema> schema2aCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(uploadSchemaService).updateSchemaRevisionV4(eq(study2Id), eq("survey-2a"), eq(21),
                schema2aCaptor.capture());
        UploadSchema schema2a = schema2aCaptor.getValue();
        assertEquals("guid-2a", schema2a.getSurveyGuid());
        assertEquals(0x2a, schema2a.getSurveyCreatedOn().longValue());

        ArgumentCaptor<UploadSchema> schema2bCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(uploadSchemaService).updateSchemaRevisionV4(eq(study2Id), eq("survey-2b"), eq(22),
                schema2bCaptor.capture());
        UploadSchema schema2b = schema2bCaptor.getValue();
        assertEquals("guid-2b", schema2b.getSurveyGuid());
        assertEquals(0x2b, schema2b.getSurveyCreatedOn().longValue());
    }

    @Test
    public void surveyWithNoSchema() {
        // Survey with no schema is skipped. Second survey to make sure the backfill continues on to the next survey.

        // mock study service
        when(studyService.getStudies()).thenReturn(ImmutableList.of(TEST_STUDY));

        // mock survey service
        Survey surveyNoSchema = new DynamoSurvey("guid-no-schema", 2222);
        surveyNoSchema.setIdentifier("survey-no-schema");
        surveyNoSchema.setSchemaRevision(null);

        when(surveyService.getAllSurveysMostRecentlyPublishedVersion(TestConstants.TEST_STUDY)).thenReturn(
                ImmutableList.of(surveyNoSchema, TEST_SURVEY));

        // mock schema service
        when(uploadSchemaService.getUploadSchemaByIdAndRev(any(), any(), anyInt())).thenAnswer(
                invocation -> new DynamoUploadSchema());

        // execute
        backfill.doBackfill(MOCK_TASK, MOCK_CALLBACK);

        // verify calls to get schema - First survey is ignored. Only second schema is updated.
        verify(uploadSchemaService, never()).updateSchemaRevisionV4(eq(TestConstants.TEST_STUDY),
                eq("survey-no-schema"), anyInt(), any());
        validateTestSurveySchema();
    }

    @Test
    public void schemaAlreadyHasSurveyFields() {
        // Schema with fields. Second survey to make sure the backfill continues on to the next survey.

        // mock study service
        when(studyService.getStudies()).thenReturn(ImmutableList.of(TEST_STUDY));

        // mock survey service
        Survey surveyWithFields = new DynamoSurvey("guid-with-fields", 3333);
        surveyWithFields.setIdentifier("survey-with-fields");
        surveyWithFields.setSchemaRevision(33);

        when(surveyService.getAllSurveysMostRecentlyPublishedVersion(TestConstants.TEST_STUDY)).thenReturn(
                ImmutableList.of(surveyWithFields, TEST_SURVEY));

        // mock schema service - This is different this time. First schema has survey fields already set. Second schema
        // does not.
        DynamoUploadSchema schemaWithFields = new DynamoUploadSchema();
        schemaWithFields.setSurveyGuid("guid-with-fields");
        schemaWithFields.setSurveyCreatedOn(3333L);
        when(uploadSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "survey-with-fields", 33))
                .thenReturn(schemaWithFields);

        when(uploadSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, TEST_SURVEY_ID,
                TEST_SURVEY_SCHEMA_REV)).thenReturn(new DynamoUploadSchema());

        // execute
        backfill.doBackfill(MOCK_TASK, MOCK_CALLBACK);

        // verify calls to get schema - First survey is ignored. Only second schema is updated.
        verify(uploadSchemaService, never()).updateSchemaRevisionV4(eq(TestConstants.TEST_STUDY),
                eq("survey-with-fields"), anyInt(), any());
        validateTestSurveySchema();
    }

    @Test
    public void getSurveysThrows() {
        // First study throws in getSurveys. Second study to make sure we continue properly.

        // mock study service
        Study errorStudy = new DynamoStudy();
        errorStudy.setIdentifier("error-study");
        StudyIdentifier errorStudyId = new StudyIdentifierImpl("error-study");
        when(studyService.getStudies()).thenReturn(ImmutableList.of(errorStudy, TEST_STUDY));

        // mock survey service
        when(surveyService.getAllSurveysMostRecentlyPublishedVersion(errorStudyId)).thenThrow(
                BridgeServiceException.class);
        when(surveyService.getAllSurveysMostRecentlyPublishedVersion(TestConstants.TEST_STUDY)).thenReturn(
                ImmutableList.of(TEST_SURVEY));

        // mock schema service
        when(uploadSchemaService.getUploadSchemaByIdAndRev(any(), any(), anyInt())).thenAnswer(
                invocation -> new DynamoUploadSchema());

        // execute
        backfill.doBackfill(MOCK_TASK, MOCK_CALLBACK);

        // verify calls to get schema - First study fails. Second study succeeds.
        verify(uploadSchemaService, never()).updateSchemaRevisionV4(eq(errorStudyId), any(), anyInt(), any());
        validateTestSurveySchema();
    }

    @Test
    public void getSchemaThrows() {
        // First schema throws. Second schema to test continue.

        // mock study service
        when(studyService.getStudies()).thenReturn(ImmutableList.of(TEST_STUDY));

        // mock survey service
        Survey badSurvey = new DynamoSurvey("error-guid", 4444);
        badSurvey.setIdentifier("error-survey");
        badSurvey.setSchemaRevision(44);

        when(surveyService.getAllSurveysMostRecentlyPublishedVersion(TestConstants.TEST_STUDY)).thenReturn(
                ImmutableList.of(badSurvey, TEST_SURVEY));

        // mock schema service - Bad survey throws. Good survey returns schema without survey attributes.
        when(uploadSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "error-survey", 44)).thenThrow(
                BridgeServiceException.class);
        when(uploadSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, TEST_SURVEY_ID,
                TEST_SURVEY_SCHEMA_REV)).thenReturn(new DynamoUploadSchema());

        // execute
        backfill.doBackfill(MOCK_TASK, MOCK_CALLBACK);

        // verify calls to get schema - First schema fails. Second schema updated.
        verify(uploadSchemaService, never()).updateSchemaRevisionV4(eq(TestConstants.TEST_STUDY), eq("error-survey"),
                anyInt(), any());
        validateTestSurveySchema();
    }

    @Test
    public void updateSchemaThrows() {
        // First schema throws. Second schema to test continue.

        // mock study service
        when(studyService.getStudies()).thenReturn(ImmutableList.of(TEST_STUDY));

        // mock survey service
        Survey badSurvey = new DynamoSurvey("error-guid", 4444);
        badSurvey.setIdentifier("error-survey");
        badSurvey.setSchemaRevision(44);

        when(surveyService.getAllSurveysMostRecentlyPublishedVersion(TestConstants.TEST_STUDY)).thenReturn(
                ImmutableList.of(badSurvey, TEST_SURVEY));

        // mock schema service
        when(uploadSchemaService.getUploadSchemaByIdAndRev(any(), any(), anyInt())).thenAnswer(
                invocation -> new DynamoUploadSchema());

        // updating "error-survey" throws.
        when(uploadSchemaService.updateSchemaRevisionV4(eq(TestConstants.TEST_STUDY), eq("error-survey"), anyInt(),
                any())).thenThrow(BridgeServiceException.class);

        // execute
        backfill.doBackfill(MOCK_TASK, MOCK_CALLBACK);

        // verify calls to get schema - First schema update is called, but fails. Second schema updated.
        ArgumentCaptor<UploadSchema> errorSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(uploadSchemaService).updateSchemaRevisionV4(eq(TestConstants.TEST_STUDY), eq("error-survey"), eq(44),
                errorSchemaCaptor.capture());
        UploadSchema errorSchema = errorSchemaCaptor.getValue();
        assertEquals("error-guid", errorSchema.getSurveyGuid());
        assertEquals(4444, errorSchema.getSurveyCreatedOn().longValue());

        validateTestSurveySchema();
    }

    private void validateTestSurveySchema() {
        ArgumentCaptor<UploadSchema> schemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(uploadSchemaService).updateSchemaRevisionV4(eq(TestConstants.TEST_STUDY), eq(TEST_SURVEY_ID),
                eq(TEST_SURVEY_SCHEMA_REV), schemaCaptor.capture());
        UploadSchema schema = schemaCaptor.getValue();
        assertEquals(TEST_SURVEY_GUID, schema.getSurveyGuid());
        assertEquals(TEST_SURVEY_CREATED_ON, schema.getSurveyCreatedOn().longValue());
    }
}
