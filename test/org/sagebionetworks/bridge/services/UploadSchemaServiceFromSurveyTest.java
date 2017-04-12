package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;
import org.sagebionetworks.bridge.models.surveys.Unit;
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
        UploadSchema oldSchema = makeSchemaForSurveyTests();
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
        UploadSchema oldSchema = makeSchemaForSurveyTests();
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

        Survey survey = makeSurveyWithElements(ImmutableList.of(infoScreen));

        // execute test - Most of this stuff is tested elsewhere, so just test result specific to this test
        try {
            svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
            fail("expected exception");
        } catch (BadRequestException ex) {
            assertEquals("Can't create a schema from a survey with no questions", ex.getMessage());
        }

        // verify calls (or lack thereof)
        verify(dao, never()).createSchemaRevision(any());
        verify(dao, never()).updateSchemaRevision(any());
    }

    @Test
    public void allSurveyFields() {
        // create survey questions
        List<SurveyElement> surveyElementList = new ArrayList<>();

        // multi-choice
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("foo"),
                    new SurveyQuestionOption("bar"), new SurveyQuestionOption("baz")));

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("multi-choice");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // multi-choice, allow other
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("foo"),
                    new SurveyQuestionOption("bar"), new SurveyQuestionOption("baz")));
            constraints.setAllowOther(true);

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("multi-choice-allow-other");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // single-choice
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(false);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("short"),
                    new SurveyQuestionOption("medium"), new SurveyQuestionOption("long")));

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("single-choice");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // duration
        {
            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("duration");
            q.setConstraints(new DurationConstraints());
            surveyElementList.add(q);
        }

        // unbounded string
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(null);

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("unbounded-string");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // long string
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(1000);

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("long-string");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // short string
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(24);

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("short-string");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // int
        {
            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("int");
            q.setConstraints(new IntegerConstraints());
            surveyElementList.add(q);
        }

        // decimal
        {
            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("decimal-q");
            q.setConstraints(new DecimalConstraints());
            surveyElementList.add(q);
        }

        // boolean
        {
            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("boolean");
            q.setConstraints(new BooleanConstraints());
            surveyElementList.add(q);
        }

        // calendar date
        {
            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("calendar-date");
            q.setConstraints(new DateConstraints());
            surveyElementList.add(q);
        }

        // local time
        {
            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("local-time");
            q.setConstraints(new TimeConstraints());
            surveyElementList.add(q);
        }

        // timestamp
        {
            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("timestamp");
            q.setConstraints(new DateTimeConstraints());
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

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
        assertEquals(SURVEY_NAME, daoInputSchema.getName());
        assertEquals(SURVEY_ID, daoInputSchema.getSchemaId());
        assertEquals(UploadSchemaType.IOS_SURVEY, daoInputSchema.getSchemaType());
        assertEquals(SURVEY_GUID, daoInputSchema.getSurveyGuid());
        assertEquals(SURVEY_CREATED_ON, daoInputSchema.getSurveyCreatedOn().longValue());

        List<UploadFieldDefinition> fieldDefList = daoInputSchema.getFieldDefinitions();
        assertEquals(16, fieldDefList.size());

        // validate that none of the fields are required
        for (UploadFieldDefinition oneFieldDef : fieldDefList) {
            assertFalse(oneFieldDef.isRequired());
        }

        // validate individual fields
        assertEquals("multi-choice", fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.MULTI_CHOICE, fieldDefList.get(0).getType());
        assertEquals(ImmutableList.of("foo", "bar", "baz"), fieldDefList.get(0).getMultiChoiceAnswerList());
        assertFalse(fieldDefList.get(0).getAllowOtherChoices());

        assertEquals("multi-choice-allow-other", fieldDefList.get(1).getName());
        assertEquals(UploadFieldType.MULTI_CHOICE, fieldDefList.get(1).getType());
        assertEquals(ImmutableList.of("foo", "bar", "baz"), fieldDefList.get(1).getMultiChoiceAnswerList());
        assertTrue(fieldDefList.get(1).getAllowOtherChoices());

        assertEquals("single-choice", fieldDefList.get(2).getName());
        assertEquals(UploadFieldType.SINGLE_CHOICE, fieldDefList.get(2).getType());
        assertEquals(UploadSchemaService.SINGLE_CHOICE_DEFAULT_LENGTH, fieldDefList.get(2).getMaxLength()
                .intValue());

        assertEquals("duration", fieldDefList.get(3).getName());
        assertEquals(UploadFieldType.INT, fieldDefList.get(3).getType());

        assertEquals("duration" + UploadUtil.UNIT_FIELD_SUFFIX, fieldDefList.get(4).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(4).getType());
        assertEquals(Unit.MAX_STRING_LENGTH, fieldDefList.get(4).getMaxLength().intValue());

        assertEquals("unbounded-string", fieldDefList.get(5).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(5).getType());
        assertTrue(fieldDefList.get(5).isUnboundedText());

        assertEquals("long-string", fieldDefList.get(6).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(6).getType());
        assertEquals(1000, fieldDefList.get(6).getMaxLength().intValue());

        assertEquals("short-string", fieldDefList.get(7).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(7).getType());
        assertEquals(24, fieldDefList.get(7).getMaxLength().intValue());

        assertEquals("int", fieldDefList.get(8).getName());
        assertEquals(UploadFieldType.INT, fieldDefList.get(8).getType());

        assertEquals("int" + UploadUtil.UNIT_FIELD_SUFFIX, fieldDefList.get(9).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(9).getType());
        assertEquals(Unit.MAX_STRING_LENGTH, fieldDefList.get(9).getMaxLength().intValue());

        assertEquals("decimal-q", fieldDefList.get(10).getName());
        assertEquals(UploadFieldType.FLOAT, fieldDefList.get(10).getType());

        assertEquals("decimal-q" + UploadUtil.UNIT_FIELD_SUFFIX, fieldDefList.get(11).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(11).getType());
        assertEquals(Unit.MAX_STRING_LENGTH, fieldDefList.get(11).getMaxLength().intValue());

        assertEquals("boolean", fieldDefList.get(12).getName());
        assertEquals(UploadFieldType.BOOLEAN, fieldDefList.get(12).getType());

        assertEquals("calendar-date", fieldDefList.get(13).getName());
        assertEquals(UploadFieldType.CALENDAR_DATE, fieldDefList.get(13).getType());

        assertEquals("local-time", fieldDefList.get(14).getName());
        assertEquals(UploadFieldType.TIME_V2, fieldDefList.get(14).getType());

        assertEquals("timestamp", fieldDefList.get(15).getName());
        assertEquals(UploadFieldType.TIMESTAMP, fieldDefList.get(15).getType());
    }

    @Test
    public void singleChoiceLengthTest() {
        UploadSchemaService.setSingleChoiceDefaultLength(2);
        try {
            List<SurveyElement> surveyElementList = new ArrayList<>();

            // single-choice, short
            {
                MultiValueConstraints constraints = new MultiValueConstraints();
                constraints.setDataType(DataType.STRING);
                constraints.setAllowMultiple(false);
                constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("aa"),
                        new SurveyQuestionOption("bb"), new SurveyQuestionOption("cc")));

                SurveyQuestion q = SurveyQuestion.create();
                q.setIdentifier("single-choice-short");
                q.setConstraints(constraints);
                surveyElementList.add(q);
            }

            // single-choice, unbounded
            {
                MultiValueConstraints constraints = new MultiValueConstraints();
                constraints.setDataType(DataType.STRING);
                constraints.setAllowMultiple(false);
                constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("foo"),
                        new SurveyQuestionOption("bar"), new SurveyQuestionOption("baz")));

                SurveyQuestion q = SurveyQuestion.create();
                q.setIdentifier("single-choice-unbounded");
                q.setConstraints(constraints);
                surveyElementList.add(q);
            }

            Survey survey = makeSurveyWithElements(surveyElementList);

            // Mock DAO. Capture input and return dummy output.
            ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
            UploadSchema daoOutputSchema = UploadSchema.create();
            when(dao.createSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

            // set up test dao and execute - Most of this stuff is tested above, so just test result specific to this
            // test
            UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
            assertSame(daoOutputSchema, svcOutputSchema);

            // verify calls
            verify(dao, never()).updateSchemaRevision(any());

            // validate created schema - We only care about the specific fields.
            UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
            List<UploadFieldDefinition> fieldDefList = daoInputSchema.getFieldDefinitions();
            assertEquals(2, fieldDefList.size());

            assertEquals("single-choice-short", fieldDefList.get(0).getName());
            assertEquals(UploadFieldType.SINGLE_CHOICE, fieldDefList.get(0).getType());
            assertEquals(2, fieldDefList.get(0).getMaxLength().intValue());

            assertEquals("single-choice-unbounded", fieldDefList.get(1).getName());
            assertEquals(UploadFieldType.SINGLE_CHOICE, fieldDefList.get(1).getType());
            assertTrue(fieldDefList.get(1).isUnboundedText());
        } finally {
            UploadSchemaService.resetSingleChoiceDefaultLength();
        }
    }

    @Test
    public void schemaFromSurveyCompatibleUpdate() {
        // The new survey removes a choice and adds a choice, removes a field and adds a field. This is successfully
        // merged into the new schema.
        List<SurveyElement> surveyElementList = new ArrayList<>();
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);
            constraints.setAllowOther(false);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("always"),
                    new SurveyQuestionOption("added-choice")));

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("multi-choice-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(24);

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("added-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

        // Mock dao to return our schema.
        UploadSchema oldSchema = makeSchemaForSurveyTests();
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

        // validate schema - Don't need to validate everything, just the essentials.
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertEquals(SURVEY_ID, daoInputSchema.getSchemaId());

        // validate fields - Merged field def list (and answer choice) list has new fields, then old fields.
        List<UploadFieldDefinition> fieldDefList = daoInputSchema.getFieldDefinitions();
        assertEquals(3, fieldDefList.size());

        assertEquals("multi-choice-q", fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.MULTI_CHOICE, fieldDefList.get(0).getType());
        assertTrue(fieldDefList.get(0).getAllowOtherChoices());
        assertEquals(ImmutableList.of("always", "added-choice", "remove-me-choice"), fieldDefList.get(0)
                .getMultiChoiceAnswerList());

        assertEquals("added-q", fieldDefList.get(1).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(1).getType());

        assertEquals("remove-me-q", fieldDefList.get(2).getName());
        assertEquals(UploadFieldType.BOOLEAN, fieldDefList.get(2).getType());
    }

    @Test
    public void schemaFromSurveyIdenticalUpdate() {
        // This schema is identical to makeSchemaForSurveyTests, but is in reverse order.
        List<SurveyElement> surveyElementList = new ArrayList<>();
        {
            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("remove-me-q");
            q.setConstraints(new BooleanConstraints());
            surveyElementList.add(q);
        }
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);
            constraints.setAllowOther(true);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("always"),
                    new SurveyQuestionOption("remove-me-choice")));

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("multi-choice-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

        // Mock dao to return our schema.
        UploadSchema oldSchema = makeSchemaForSurveyTests();
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SURVEY_ID)).thenReturn(oldSchema);

        // set up test dao and execute
        UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
        assertSame(oldSchema, svcOutputSchema);

        // verify calls
        verify(dao, never()).createSchemaRevision(any());
        verify(dao, never()).updateSchemaRevision(any());

        // Don't need to validate the schema. We know it's the same as the old one.
    }

    @Test
    public void schemaFromSurveyIncompatibleUpdate() {
        // The new survey changes the answer list in multi-choice-q, which is fine. But it also changes the type of
        // remove-me-q, which is not fine.
        List<SurveyElement> surveyElementList = new ArrayList<>();
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);
            constraints.setAllowOther(false);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("always"),
                    new SurveyQuestionOption("added-choice")));

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("multi-choice-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(24);

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("remove-me-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

        // Mock dao to return our schema.
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SURVEY_ID)).thenReturn(
                makeSchemaForSurveyTests());

        // Mock DAO. Capture input and return dummy output.
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = UploadSchema.create();
        when(dao.createSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        // set up test dao and execute
        UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, false);
        assertSame(daoOutputSchema, svcOutputSchema);

        // verify calls
        verify(dao, never()).updateSchemaRevision(any());

        // validate schema - Don't need to validate everything, just the essentials.
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertEquals(SURVEY_ID, daoInputSchema.getSchemaId());

        // validate fields - It's an incompatible change, so just the new field def list.
        List<UploadFieldDefinition> fieldDefList = daoInputSchema.getFieldDefinitions();
        assertEquals(2, fieldDefList.size());

        assertEquals("multi-choice-q", fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.MULTI_CHOICE, fieldDefList.get(0).getType());
        assertFalse(fieldDefList.get(0).getAllowOtherChoices());
        assertEquals(ImmutableList.of("always", "added-choice"), fieldDefList.get(0).getMultiChoiceAnswerList());

        assertEquals("remove-me-q", fieldDefList.get(1).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(1).getType());
    }

    @Test
    public void schemaFromSurveyExplicitNewRev() {
        // This survey is compatible with the old schema, but we explicitly ask for a new rev.
        Survey survey = makeSimpleSurvey();

        // Mock dao to return our schema.
        UploadSchema oldSchema = makeSchemaForSurveyTests();
        when(dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SURVEY_ID)).thenReturn(oldSchema);

        // Mock DAO. Capture input and return dummy output.
        ArgumentCaptor<UploadSchema> daoInputSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        UploadSchema daoOutputSchema = UploadSchema.create();
        when(dao.createSchemaRevision(daoInputSchemaCaptor.capture())).thenReturn(daoOutputSchema);

        // set up test dao and execute
        UploadSchema svcOutputSchema = svc.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, true);
        assertSame(daoOutputSchema, svcOutputSchema);

        // verify calls
        verify(dao, never()).updateSchemaRevision(any());

        // validate schema - Don't need to validate everything, just the essentials.
        UploadSchema daoInputSchema = daoInputSchemaCaptor.getValue();
        assertEquals(SURVEY_ID, daoInputSchema.getSchemaId());

        // validate fields
        // Fields are the same as the old schema. We get a new schema rev anyway because we set the newSchemaRev flag.
        assertEquals(oldSchema.getFieldDefinitions(), daoInputSchema.getFieldDefinitions());
    }

    @Test
    public void mergeSurveySchemaFields() {
        // List of fields that can be successfully merged:
        // * same field in both lists
        // * added field
        // * removed field
        // * multi-choice, where the new field has different answers and allowOther true -> false
        // * string with maxLength reduced
        // * unbounded string changed to bounded string

        List<UploadFieldDefinition> oldFieldDefList = new ArrayList<>();
        List<UploadFieldDefinition> newFieldDefList = new ArrayList<>();

        // same field in both
        {
            UploadFieldDefinition field = new UploadFieldDefinition.Builder().withName("same")
                    .withType(UploadFieldType.BOOLEAN).withRequired(false).build();
            oldFieldDefList.add(field);
            newFieldDefList.add(field);
        }

        // added field
        {
            UploadFieldDefinition field = new UploadFieldDefinition.Builder().withName("added")
                    .withType(UploadFieldType.BOOLEAN).withRequired(false).build();
            // not added to old
            newFieldDefList.add(field);
        }

        // removed field
        {
            UploadFieldDefinition field = new UploadFieldDefinition.Builder().withName("removed")
                    .withType(UploadFieldType.BOOLEAN).withRequired(false).build();
            oldFieldDefList.add(field);
            // not added to new
        }

        // multi-choice
        {
            UploadFieldDefinition oldField = new UploadFieldDefinition.Builder().withName("multi-choice")
                    .withType(UploadFieldType.MULTI_CHOICE).withRequired(false)
                    .withMultiChoiceAnswerList("same", "removed").withAllowOtherChoices(true).build();
            oldFieldDefList.add(oldField);

            UploadFieldDefinition newField = new UploadFieldDefinition.Builder().withName("multi-choice")
                    .withType(UploadFieldType.MULTI_CHOICE).withRequired(false)
                    .withMultiChoiceAnswerList("same", "added").withAllowOtherChoices(false).build();
            newFieldDefList.add(newField);
        }

        // string, maxLength reduced
        {
            UploadFieldDefinition oldField = new UploadFieldDefinition.Builder().withName("string-with-length")
                    .withType(UploadFieldType.STRING).withRequired(false).withMaxLength(128).build();
            oldFieldDefList.add(oldField);

            UploadFieldDefinition newField = new UploadFieldDefinition.Builder().withName("string-with-length")
                    .withType(UploadFieldType.STRING).withRequired(false).withMaxLength(24).build();
            newFieldDefList.add(newField);
        }

        // string, bounded to unbounded
        {
            UploadFieldDefinition oldField = new UploadFieldDefinition.Builder().withName("unbounded-string")
                    .withType(UploadFieldType.STRING).withRequired(false).withUnboundedText(true).build();
            oldFieldDefList.add(oldField);

            UploadFieldDefinition newField = new UploadFieldDefinition.Builder().withName("unbounded-string")
                    .withType(UploadFieldType.STRING).withRequired(false).withMaxLength(128).build();
            newFieldDefList.add(newField);
        }

        // sanity check setup
        assertEquals(5, oldFieldDefList.size());
        assertEquals(5, newFieldDefList.size());

        // execute and validate - Removed fields show up last.
        UploadSchemaService.MergeSurveySchemaResult mergeResult = UploadSchemaService.mergeSurveySchemaFields(
                oldFieldDefList, newFieldDefList);
        assertTrue(mergeResult.isSuccess());

        List<UploadFieldDefinition> mergedFieldDefList = mergeResult.getFieldDefinitionList();
        assertEquals(6, mergedFieldDefList.size());

        assertEquals("same", mergedFieldDefList.get(0).getName());
        assertEquals(UploadFieldType.BOOLEAN, mergedFieldDefList.get(0).getType());

        assertEquals("added", mergedFieldDefList.get(1).getName());
        assertEquals(UploadFieldType.BOOLEAN, mergedFieldDefList.get(1).getType());

        assertEquals("multi-choice", mergedFieldDefList.get(2).getName());
        assertEquals(UploadFieldType.MULTI_CHOICE, mergedFieldDefList.get(2).getType());
        assertEquals(ImmutableList.of("same", "added", "removed"), mergedFieldDefList.get(2)
                .getMultiChoiceAnswerList());
        assertTrue(mergedFieldDefList.get(2).getAllowOtherChoices());

        assertEquals("string-with-length", mergedFieldDefList.get(3).getName());
        assertEquals(UploadFieldType.STRING, mergedFieldDefList.get(3).getType());
        assertEquals(128, mergedFieldDefList.get(3).getMaxLength().intValue());

        assertEquals("unbounded-string", mergedFieldDefList.get(4).getName());
        assertEquals(UploadFieldType.STRING, mergedFieldDefList.get(4).getType());
        assertTrue(mergedFieldDefList.get(4).isUnboundedText());
        assertNull(mergedFieldDefList.get(4).getMaxLength());

        assertEquals("removed", mergedFieldDefList.get(5).getName());
        assertEquals(UploadFieldType.BOOLEAN, mergedFieldDefList.get(5).getType());
    }

    @Test
    public void mergeSurveySchemaFieldsNotCompatible() {
        // incompatible field, like changing a short string to an unbounded string
        List<UploadFieldDefinition> oldFieldDefList = ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("field").withType(UploadFieldType.STRING).withMaxLength(24).build());
        List<UploadFieldDefinition> newFieldDefList = ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("field").withType(UploadFieldType.STRING).withUnboundedText(true).build());

        // execute and validate
        UploadSchemaService.MergeSurveySchemaResult mergeResult = UploadSchemaService.mergeSurveySchemaFields(
                oldFieldDefList, newFieldDefList);
        assertFalse(mergeResult.isSuccess());
        assertEquals(newFieldDefList, mergeResult.getFieldDefinitionList());

    }

    @Test
    public void mergeSurveySchemaFieldsDifferentType() {
        // changing the field type will cause things to break.
        List<UploadFieldDefinition> oldFieldDefList = ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("field").withType(UploadFieldType.STRING).withMaxLength(24).build());
        List<UploadFieldDefinition> newFieldDefList = ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("field").withType(UploadFieldType.BOOLEAN).build());

        // execute and validate
        UploadSchemaService.MergeSurveySchemaResult mergeResult = UploadSchemaService.mergeSurveySchemaFields(
                oldFieldDefList, newFieldDefList);
        assertFalse(mergeResult.isSuccess());
        assertEquals(newFieldDefList, mergeResult.getFieldDefinitionList());

    }

    // Makes a survey that matches makeSchemaForSurveyTests()
    private static Survey makeSimpleSurvey() {
        List<SurveyElement> surveyElementList = new ArrayList<>();
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);
            constraints.setAllowOther(true);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("always"),
                    new SurveyQuestionOption("remove-me-choice")));

            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("multi-choice-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }
        {
            SurveyQuestion q = SurveyQuestion.create();
            q.setIdentifier("remove-me-q");
            q.setConstraints(new BooleanConstraints());
            surveyElementList.add(q);
        }

        return makeSurveyWithElements(surveyElementList);
    }

    // Makes a survey with any element list.
    private static Survey makeSurveyWithElements(List<SurveyElement> surveyElementList) {
        Survey survey = Survey.create();
        survey.setGuid(SURVEY_GUID);
        survey.setCreatedOn(SURVEY_CREATED_ON);
        survey.setIdentifier(SURVEY_ID);
        survey.setName(SURVEY_NAME);
        survey.setElements(surveyElementList);
        return survey;
    }

    // Makes a schema that matches makeSimpleSurvey()
    private static UploadSchema makeSchemaForSurveyTests() {
        // 2 simple fields, one multi-choice, one bool
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("multi-choice-q")
                        .withType(UploadFieldType.MULTI_CHOICE).withRequired(false)
                        .withMultiChoiceAnswerList("always", "remove-me-choice").withAllowOtherChoices(true).build(),
                new UploadFieldDefinition.Builder().withName("remove-me-q").withType(UploadFieldType.BOOLEAN)
                        .withRequired(false).build());

        // For these tests, we don't need all fields, just the field def list, type, ddb version, and revision.
        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(fieldDefList);
        schema.setRevision(SCHEMA_REV);
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setSurveyGuid(SURVEY_GUID);
        return schema;
    }
}
