package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.upload.UploadUtil;

public class UploadSchemaServiceFromSurveyTest {
    private static final int SCHEMA_REV = 3;
    private static final long SURVEY_CREATED_ON = 1337;
    private static final String SURVEY_GUID = "test-guid";
    private static final String SURVEY_ID = "test-survey";
    private static final String SURVEY_NAME = "Test Survey";

    private UploadSchemaService svc;
    private UploadSchemaDao dao;

    @Before
    public void setup() {
        dao = mock(UploadSchemaDao.class);
        svc = new UploadSchemaService();
        svc.setUploadSchemaDao(dao);
    }

    @Test
    public void oldSchemaIsDataSchema() {
        // Mock dao to return schema with IOS_DATA type.
        UploadSchema oldSchema = makeBackwardsCompatibleSchemaForSurveyTests();
        oldSchema.setSchemaType(UploadSchemaType.IOS_DATA);
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SURVEY_ID)).thenReturn(oldSchema);

        // Set up test inputs
        Survey survey = makeSimpleSurvey();

        // execute - will throw
        try {
            svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
            fail("expected exception");
        } catch (BadRequestException ex) {
            assertEquals("Survey with identifier " + SURVEY_ID +
                            " conflicts with schema with the same ID. Please use a different survey identifier.",
                    ex.getMessage());
        }

        // verify calls (or lack thereof)
        verify(dao, never()).createSchemaRevision(any());
        verify(dao, never()).updateSchemaRevision(any());
    }

    @Test
    public void oldSchemaIsWrongSurvey() {
        // Mock dao to return schema with wrong survey guid
        UploadSchema oldSchema = makeBackwardsCompatibleSchemaForSurveyTests();
        oldSchema.setSurveyGuid("wrong-guid");
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SURVEY_ID)).thenReturn(oldSchema);

        // Set up test inputs
        Survey survey = makeSimpleSurvey();

        // execute - will throw
        try {
            svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
            fail("expected exception");
        } catch (BadRequestException ex) {
            assertEquals("Survey with identifier " + SURVEY_ID +
                            " conflicts with schema with the same ID. Please use a different survey identifier.",
                    ex.getMessage());
        }

        // verify calls (or lack thereof)
        verify(dao, never()).createSchemaRevision(any());
        verify(dao, never()).updateSchemaRevision(any());
    }

    @Test
    public void infoScreensOnly() {
        // create a survey with an info screen and no questions
        SurveyInfoScreen infoScreen = SurveyInfoScreen.create();
        infoScreen.setIdentifier("test-info-screen");
        infoScreen.setTitle("Test Info Screen");
        infoScreen.setPrompt("This info screen doesn't do anything, other than not being a question.");

        Survey survey = makeSurveyWithElements(infoScreen);

        // Mock DAO. Capture input and return dummy output.
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = UploadSchema.create();
        when(dao.createSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        // set up test dao and execute
        UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
        assertSame(daoOutputSchema, svcOutputSchema);

        // verify calls
        verify(dao, never()).updateSchemaRevision(any());

        // validate schema
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertSchema(daoInputSchema, UploadUtil.ANSWERS_FIELD_DEF);
    }

    @Test
    public void normalCase() {
        // create survey questions
        Survey survey = makeSimpleSurvey();

        // Mock DAO. Capture input and return dummy output.
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = UploadSchema.create();
        when(dao.createSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        // set up test dao and execute
        UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
        assertSame(daoOutputSchema, svcOutputSchema);

        // verify calls
        verify(dao, never()).updateSchemaRevision(any());

        // validate schema
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertSchema(daoInputSchema, UploadUtil.ANSWERS_FIELD_DEF);
    }

    @Test
    public void newSchemaRevTrue() {
        // create survey questions
        Survey survey = makeSimpleSurvey();

        // Mock dao to return old schema.
        UploadSchema oldSchema = makeBackwardsCompatibleSchemaForSurveyTests();
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SURVEY_ID)).thenReturn(oldSchema);
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SURVEY_ID, SCHEMA_REV)).thenReturn(
                oldSchema);

        // Mock DAO. Capture input and return dummy output.
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = UploadSchema.create();
        when(dao.createSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        // set up test dao and execute
        UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, true);
        assertSame(daoOutputSchema, svcOutputSchema);

        // verify calls
        verify(dao, never()).updateSchemaRevision(any());

        // validate schema
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertSchema(daoInputSchema, UploadUtil.ANSWERS_FIELD_DEF);
    }

    @Test
    public void addToExistingSchemaRev() {
        // create survey questions
        Survey survey = makeSimpleSurvey();

        // Mock dao to return old schema.
        UploadSchema oldSchema = makeBackwardsCompatibleSchemaForSurveyTests();
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SURVEY_ID)).thenReturn(oldSchema);
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SURVEY_ID, SCHEMA_REV)).thenReturn(
                oldSchema);

        // Mock DAO. Capture input and return dummy output.
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = UploadSchema.create();
        when(dao.updateSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        // set up test dao and execute
        UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
        assertSame(daoOutputSchema, svcOutputSchema);

        // verify calls
        verify(dao, never()).createSchemaRevision(any());

        // validate schema
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertSchema(daoInputSchema, oldSchema.getFieldDefinitions().get(0), UploadUtil.ANSWERS_FIELD_DEF);
    }

    @Test
    public void existingCompatibleAttachmentField() {
        // Make it not required to show that it doesn't matter.
        UploadFieldDefinition oldSchemaFieldDef = new UploadFieldDefinition.Builder().withName(UploadUtil.FIELD_ANSWERS)
                .withRequired(false).withType(UploadFieldType.ATTACHMENT_V2).build();
        existingCompatibleField(oldSchemaFieldDef);
    }

    @Test
    public void existingCompatibleLargeTextAttachmentField() {
        UploadFieldDefinition oldSchemaFieldDef = new UploadFieldDefinition.Builder().withName(UploadUtil.FIELD_ANSWERS)
                .withRequired(false).withType(UploadFieldType.LARGE_TEXT_ATTACHMENT).build();
        existingCompatibleField(oldSchemaFieldDef);
    }

    @Test
    public void existingCompatibleUnboundedStringField() {
        UploadFieldDefinition oldSchemaFieldDef = new UploadFieldDefinition.Builder().withName(UploadUtil.FIELD_ANSWERS)
                .withRequired(false).withType(UploadFieldType.STRING).withUnboundedText(true).build();
        existingCompatibleField(oldSchemaFieldDef);
    }

    private void existingCompatibleField(UploadFieldDefinition oldSchemaFieldDef) {
        // create survey questions
        Survey survey = makeSimpleSurvey();

        // Mock dao to return old schema.
        UploadSchema oldSchema = makeSchemaWithFields(oldSchemaFieldDef);
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SURVEY_ID)).thenReturn(oldSchema);
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SURVEY_ID, SCHEMA_REV)).thenReturn(
                oldSchema);

        // Mock DAO. Capture input and return dummy output.
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = UploadSchema.create();
        when(dao.updateSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        // set up test dao and execute
        UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
        assertSame(daoOutputSchema, svcOutputSchema);

        // verify calls
        verify(dao, never()).createSchemaRevision(any());

        // validate schema
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertSchema(daoInputSchema, oldSchemaFieldDef);

    }

    @Test
    public void exitingIncompatibleStringField() {
        UploadFieldDefinition oldSchemaFieldDef = new UploadFieldDefinition.Builder().withName(UploadUtil.FIELD_ANSWERS)
                .withRequired(false).withType(UploadFieldType.STRING).withUnboundedText(false).withMaxLength(1000)
                .build();
        existingIncompatibleField(oldSchemaFieldDef);
    }

    @Test
    public void exitingIncompatibleNotAStringField() {
        UploadFieldDefinition oldSchemaFieldDef = new UploadFieldDefinition.Builder().withName(UploadUtil.FIELD_ANSWERS)
                .withRequired(false).withType(UploadFieldType.INT).build();
        existingIncompatibleField(oldSchemaFieldDef);
    }

    private void existingIncompatibleField(UploadFieldDefinition oldSchemaFieldDef) {
        // create survey questions
        Survey survey = makeSimpleSurvey();

        // Mock dao to return old schema.
        UploadSchema oldSchema = makeSchemaWithFields(oldSchemaFieldDef);
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SURVEY_ID)).thenReturn(oldSchema);
        when(dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SURVEY_ID, SCHEMA_REV)).thenReturn(
                oldSchema);

        // Mock DAO. Capture input and return dummy output.
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = UploadSchema.create();
        when(dao.createSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        // set up test dao and execute
        UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
        assertSame(daoOutputSchema, svcOutputSchema);

        // verify calls
        verify(dao, never()).updateSchemaRevision(any());

        // validate schema
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertSchema(daoInputSchema, UploadUtil.ANSWERS_FIELD_DEF);
    }

    // Makes a survey that matches makeBackwardsCompatibleSchemaForSurveyTests()
    private static Survey makeSimpleSurvey() {
        MultiValueConstraints constraints = new MultiValueConstraints();
        constraints.setDataType(DataType.STRING);
        constraints.setAllowMultiple(true);
        constraints.setAllowOther(true);
        constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("always"),
                new SurveyQuestionOption("bar"), new SurveyQuestionOption("baz")));

        SurveyQuestion q = SurveyQuestion.create();
        q.setIdentifier("multi-choice-q");
        q.setConstraints(constraints);

        return makeSurveyWithElements(q);
    }

    // Makes a survey with any element list.
    private static Survey makeSurveyWithElements(SurveyElement... surveyElementVarargs) {
        Survey survey = Survey.create();
        survey.setGuid(SURVEY_GUID);
        survey.setCreatedOn(SURVEY_CREATED_ON);
        survey.setIdentifier(SURVEY_ID);
        survey.setName(SURVEY_NAME);
        survey.setElements(ImmutableList.copyOf(surveyElementVarargs));
        return survey;
    }

    // Makes a schema that uses the old format with a field for every question.
    private static UploadSchema makeBackwardsCompatibleSchemaForSurveyTests() {
        // Simple multi-choice field.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("multi-choice-q")
                .withType(UploadFieldType.MULTI_CHOICE).withRequired(false)
                .withMultiChoiceAnswerList("foo", "bar", "baz").withAllowOtherChoices(true).build();
        return makeSchemaWithFields(fieldDef);
    }

    // Makes a schema with the given fields.
    private static UploadSchema makeSchemaWithFields(UploadFieldDefinition... fieldDefVarargs) {
        // For these tests, we don't need all fields, just the field def list, type, ddb version, and revision.
        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(ImmutableList.copyOf(fieldDefVarargs));
        schema.setRevision(SCHEMA_REV);
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setSurveyGuid(SURVEY_GUID);
        return schema;
    }

    private static void assertSchema(UploadSchema schema, UploadFieldDefinition... expectedFieldDefVarargs) {
        assertEquals(ImmutableList.copyOf(expectedFieldDefVarargs), schema.getFieldDefinitions());
        assertEquals(SURVEY_NAME, schema.getName());
        assertEquals(SURVEY_ID, schema.getSchemaId());
        assertEquals(UploadSchemaType.IOS_SURVEY, schema.getSchemaType());
        assertEquals(SURVEY_GUID, schema.getSurveyGuid());
        assertEquals(SURVEY_CREATED_ON, schema.getSurveyCreatedOn().longValue());
    }
}
