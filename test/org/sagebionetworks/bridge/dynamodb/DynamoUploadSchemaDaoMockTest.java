package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
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
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;
import org.sagebionetworks.bridge.models.surveys.Unit;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.upload.UploadUtil;

@SuppressWarnings({ "ConstantConditions", "rawtypes", "RedundantCast", "unchecked" })
public class DynamoUploadSchemaDaoMockTest {
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;

    private static final long SURVEY_CREATED_ON = 1337;
    private static final String SURVEY_GUID = "test-guid";
    private static final String SURVEY_ID = "test-survey";
    private static final String SURVEY_NAME = "Test Survey";
    private static final long SURVEY_SCHEMA_DDB_VERSION = 2;

    @Test
    public void createNewSchema() {
        testCreateUpdate("createStudy", "newSchema", null, 0);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void createNewSchemaAlreadyExists() {
        testCreateUpdate("createStudy", "newSchemaAlreadyExists", 1, 0);
    }

    @Test
    public void updateSchema() {
        testCreateUpdate("updateStudy", "updatedSchema", 1, 1);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void updateSchemaWrongRevision() {
        testCreateUpdate("updateStudy", "updatedSchemaWrongRevision", 3, 2);
    }

    private static void testCreateUpdate(String studyId, String schemaId, Integer oldRev, int newRev) {
        // mock DDB mapper
        DynamoUploadSchema schema = null;
        if (oldRev != null) {
            schema = makeUploadSchema(studyId, schemaId, oldRev);
        }
        DynamoDBMapper mockMapper = setupMockMapperWithSchema(schema);

        // Make schema that we create/update. Add a version number 1 just to make sure the DAO clears it.
        DynamoUploadSchema schemaToPost = makeUploadSchema(null, schemaId, newRev);
        schemaToPost.setVersion(1L);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        UploadSchema retVal = dao.createOrUpdateUploadSchema(studyId, schemaToPost);

        // Validate call to DDB - we can't compare if the captured argument is equal to the passed in upload
        // schema since DDB objects are mutable.
        ArgumentCaptor<DynamoUploadSchema> arg = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        verify(mockMapper).save(arg.capture(), notNull(DynamoDBSaveExpression.class));
        assertEquals(schemaId, arg.getValue().getSchemaId());

        // validate that study ID was injected
        assertEquals(studyId, arg.getValue().getStudyId());

        // DAO auto-increments the revision
        assertEquals(newRev + 1, arg.getValue().getRevision());

        // validate we clear the DDB version
        assertNull(arg.getValue().getVersion());

        // The retVal should be the same object as the schema was sent to DDB.
        assertSame(arg.getValue(), retVal);
    }

    private static Survey makeSurveyWithElements(List<SurveyElement> surveyElementList) {
        Survey survey = new DynamoSurvey();
        survey.setGuid(SURVEY_GUID);
        survey.setCreatedOn(SURVEY_CREATED_ON);
        survey.setIdentifier(SURVEY_ID);
        survey.setName(SURVEY_NAME);
        survey.setElements(surveyElementList);
        return survey;
    }

    @Test
    public void createUploadSchemaFromSurveyAllFields() {
        // create survey questions
        List<SurveyElement> surveyElementList = new ArrayList<>();

        // multi-choice
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("foo"),
                    new SurveyQuestionOption("bar"), new SurveyQuestionOption("baz")));

            SurveyQuestion q = new DynamoSurveyQuestion();
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

            SurveyQuestion q = new DynamoSurveyQuestion();
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

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("single-choice");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // duration
        {
            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("duration");
            q.setConstraints(new DurationConstraints());
            surveyElementList.add(q);
        }

        // unbounded string
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(null);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("unbounded-string");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // long string
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(1000);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("long-string");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // short string
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(24);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("short-string");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // int
        {
            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("int");
            q.setConstraints(new IntegerConstraints());
            surveyElementList.add(q);
        }

        // decimal
        {
            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("decimal-q");
            q.setConstraints(new DecimalConstraints());
            surveyElementList.add(q);
        }

        // boolean
        {
            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("boolean");
            q.setConstraints(new BooleanConstraints());
            surveyElementList.add(q);
        }

        // calendar date
        {
            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("calendar-date");
            q.setConstraints(new DateConstraints());
            surveyElementList.add(q);
        }

        // local time
        {
            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("local-time");
            q.setConstraints(new TimeConstraints());
            surveyElementList.add(q);
        }

        // timestamp
        {
            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("timestamp");
            q.setConstraints(new DateTimeConstraints());
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

        // There's a lot of complex logic under test. To de-couple the "create a schema from a survey" part from the
        // "interface with DDB" part, we're just going to spy out getUploadSchemaNoThrow(), createSchemaV4() and
        // updateSchemaV4().
        UploadSchema dummy = new DynamoUploadSchema();
        DynamoUploadSchemaDao dao = spy(new DynamoUploadSchemaDao());
        doReturn(null).when(dao).getUploadSchemaNoThrow(any(), any());
        doReturn(dummy).when(dao).createSchemaRevisionV4(any(), any());

        // set up test dao and execute
        DynamoUploadSchema retval = (DynamoUploadSchema) dao.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY,
                survey, false);
        assertSame(dummy, retval);

        // verify calls
        verify(dao).getUploadSchemaNoThrow(TestConstants.TEST_STUDY_IDENTIFIER, SURVEY_ID);
        verify(dao, never()).updateSchemaRevisionV4(any(), any(), anyInt(), any());

        ArgumentCaptor<UploadSchema> createdSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(dao).createSchemaRevisionV4(eq(TestConstants.TEST_STUDY), createdSchemaCaptor.capture());

        // validate schema
        UploadSchema createdSchema = createdSchemaCaptor.getValue();
        assertEquals(SURVEY_NAME, createdSchema.getName());
        assertEquals(SURVEY_ID, createdSchema.getSchemaId());
        assertEquals(UploadSchemaType.IOS_SURVEY, createdSchema.getSchemaType());
        assertEquals(SURVEY_GUID, createdSchema.getSurveyGuid());
        assertEquals(SURVEY_CREATED_ON, createdSchema.getSurveyCreatedOn().longValue());

        List<UploadFieldDefinition> fieldDefList = createdSchema.getFieldDefinitions();
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
        assertEquals(6, fieldDefList.get(2).getMaxLength().intValue());

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

    private static DynamoUploadSchema makeSchemaForSurveyTests() {
        // 2 simple fields, one multi-choice, one bool
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("multi-choice-q")
                        .withType(UploadFieldType.MULTI_CHOICE).withRequired(false)
                        .withMultiChoiceAnswerList("always", "remove-me-choice").withAllowOtherChoices(true).build(),
                new DynamoUploadFieldDefinition.Builder().withName("remove-me-q").withType(UploadFieldType.BOOLEAN)
                        .withRequired(false).build());

        // For these tests, we don't need all fields, just the field def list, type, ddb version, and revision.
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setFieldDefinitions(fieldDefList);
        schema.setRevision(SCHEMA_REV);
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setVersion(SURVEY_SCHEMA_DDB_VERSION);
        return schema;
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

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("multi-choice-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(24);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("added-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

        // Similarly, spy UploadSchemaDao.
        UploadSchema dummy = new DynamoUploadSchema();
        DynamoUploadSchemaDao dao = spy(new DynamoUploadSchemaDao());
        doReturn(makeSchemaForSurveyTests()).when(dao).getUploadSchemaNoThrow(any(), any());
        doReturn(dummy).when(dao).updateSchemaRevisionV4(any(), any(), anyInt(), any());

        // set up test dao and execute
        DynamoUploadSchema retval = (DynamoUploadSchema) dao.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY,
                survey, false);
        assertSame(dummy, retval);

        // verify calls
        verify(dao).getUploadSchemaNoThrow(TestConstants.TEST_STUDY_IDENTIFIER, SURVEY_ID);
        verify(dao, never()).createSchemaRevisionV4(any(), any());

        ArgumentCaptor<UploadSchema> updatedSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(dao).updateSchemaRevisionV4(eq(TestConstants.TEST_STUDY), eq(SURVEY_ID), eq(SCHEMA_REV),
                updatedSchemaCaptor.capture());

        // validate schema - Don't need to validate everything, just the essentials.
        UploadSchema updatedSchema = updatedSchemaCaptor.getValue();
        assertEquals(SURVEY_ID, updatedSchema.getSchemaId());
        assertEquals(SURVEY_SCHEMA_DDB_VERSION, updatedSchema.getVersion().longValue());

        // validate fields - Merged field def list (and answer choice) list has new fields, then old fields.
        List<UploadFieldDefinition> fieldDefList = updatedSchema.getFieldDefinitions();
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
            SurveyQuestion q = new DynamoSurveyQuestion();
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

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("multi-choice-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

        // Similarly, spy UploadSchemaDao.
        UploadSchema oldSchema = makeSchemaForSurveyTests();
        DynamoUploadSchemaDao dao = spy(new DynamoUploadSchemaDao());
        doReturn(oldSchema).when(dao).getUploadSchemaNoThrow(any(), any());

        // set up test dao and execute
        DynamoUploadSchema retval = (DynamoUploadSchema) dao.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY,
                survey, false);
        assertSame(oldSchema, retval);

        // verify calls
        verify(dao).getUploadSchemaNoThrow(TestConstants.TEST_STUDY_IDENTIFIER, SURVEY_ID);
        verify(dao, never()).createSchemaRevisionV4(any(), any());
        verify(dao, never()).updateSchemaRevisionV4(any(), any(), anyInt(), any());

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

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("multi-choice-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(24);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("remove-me-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

        // Similarly, spy UploadSchemaDao.
        UploadSchema dummy = new DynamoUploadSchema();
        DynamoUploadSchemaDao dao = spy(new DynamoUploadSchemaDao());
        doReturn(makeSchemaForSurveyTests()).when(dao).getUploadSchemaNoThrow(any(), any());
        doReturn(dummy).when(dao).createSchemaRevisionV4(any(), any());

        // set up test dao and execute
        DynamoUploadSchema retval = (DynamoUploadSchema) dao.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY,
                survey, false);
        assertSame(dummy, retval);

        // verify calls
        verify(dao).getUploadSchemaNoThrow(TestConstants.TEST_STUDY_IDENTIFIER, SURVEY_ID);
        verify(dao, never()).updateSchemaRevisionV4(any(), any(), anyInt(), any());

        ArgumentCaptor<UploadSchema> createdSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(dao).createSchemaRevisionV4(eq(TestConstants.TEST_STUDY), createdSchemaCaptor.capture());

        // validate schema - Don't need to validate everything, just the essentials.
        UploadSchema createdSchema = createdSchemaCaptor.getValue();
        assertEquals(SURVEY_ID, createdSchema.getSchemaId());

        // validate fields - It's an incompatible change, so just the new field def list.
        List<UploadFieldDefinition> fieldDefList = createdSchema.getFieldDefinitions();
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
        List<SurveyElement> surveyElementList = new ArrayList<>();
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);
            constraints.setAllowOther(false);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("always"),
                    new SurveyQuestionOption("added-choice")));

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("multi-choice-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(24);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("added-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

        // Similarly, spy UploadSchemaDao.
        UploadSchema dummy = new DynamoUploadSchema();
        DynamoUploadSchemaDao dao = spy(new DynamoUploadSchemaDao());
        doReturn(dummy).when(dao).createSchemaRevisionV4(any(), any());

        // set up test dao and execute
        DynamoUploadSchema retval = (DynamoUploadSchema) dao.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY,
                survey, true);
        assertSame(dummy, retval);

        // verify calls - Note that because we ask for a new rev, we never call to get the old schema.
        verify(dao, never()).getUploadSchemaNoThrow(any(), any());
        verify(dao, never()).updateSchemaRevisionV4(any(), any(), anyInt(), any());

        ArgumentCaptor<UploadSchema> createdSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(dao).createSchemaRevisionV4(eq(TestConstants.TEST_STUDY), createdSchemaCaptor.capture());

        // validate schema - Don't need to validate everything, just the essentials.
        UploadSchema createdSchema = createdSchemaCaptor.getValue();
        assertEquals(SURVEY_ID, createdSchema.getSchemaId());

        // validate fields
        List<UploadFieldDefinition> fieldDefList = createdSchema.getFieldDefinitions();
        assertEquals(2, fieldDefList.size());

        assertEquals("multi-choice-q", fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.MULTI_CHOICE, fieldDefList.get(0).getType());
        assertFalse(fieldDefList.get(0).getAllowOtherChoices());
        assertEquals(ImmutableList.of("always", "added-choice"), fieldDefList.get(0).getMultiChoiceAnswerList());

        assertEquals("added-q", fieldDefList.get(1).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(1).getType());
    }

    @Test
    public void schemaFromSurveyOldSchemaIsDataSchema() {
        // This survey would have been compatible with the old schema, except the old schema is a non-survey schema.
        List<SurveyElement> surveyElementList = new ArrayList<>();
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);
            constraints.setAllowOther(false);
            constraints.setEnumeration(ImmutableList.of(new SurveyQuestionOption("always"),
                    new SurveyQuestionOption("added-choice")));

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("multi-choice-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }
        {
            StringConstraints constraints = new StringConstraints();
            constraints.setMaxLength(24);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("added-q");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        Survey survey = makeSurveyWithElements(surveyElementList);

        // Similarly, spy UploadSchemaDao.
        DynamoUploadSchema oldSchema = makeSchemaForSurveyTests();
        oldSchema.setSchemaType(UploadSchemaType.IOS_DATA);

        UploadSchema dummy = new DynamoUploadSchema();
        DynamoUploadSchemaDao dao = spy(new DynamoUploadSchemaDao());
        doReturn(oldSchema).when(dao).getUploadSchemaNoThrow(any(), any());
        doReturn(dummy).when(dao).createSchemaRevisionV4(any(), any());

        // set up test dao and execute
        DynamoUploadSchema retval = (DynamoUploadSchema) dao.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY,
                survey, false);
        assertSame(dummy, retval);

        // verify calls
        verify(dao).getUploadSchemaNoThrow(TestConstants.TEST_STUDY_IDENTIFIER, SURVEY_ID);
        verify(dao, never()).updateSchemaRevisionV4(any(), any(), anyInt(), any());

        ArgumentCaptor<UploadSchema> createdSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(dao).createSchemaRevisionV4(eq(TestConstants.TEST_STUDY), createdSchemaCaptor.capture());

        // validate schema - Don't need to validate everything, just the essentials.
        UploadSchema createdSchema = createdSchemaCaptor.getValue();
        assertEquals(SURVEY_ID, createdSchema.getSchemaId());

        // validate fields
        List<UploadFieldDefinition> fieldDefList = createdSchema.getFieldDefinitions();
        assertEquals(2, fieldDefList.size());

        assertEquals("multi-choice-q", fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.MULTI_CHOICE, fieldDefList.get(0).getType());
        assertFalse(fieldDefList.get(0).getAllowOtherChoices());
        assertEquals(ImmutableList.of("always", "added-choice"), fieldDefList.get(0).getMultiChoiceAnswerList());

        assertEquals("added-q", fieldDefList.get(1).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(1).getType());
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
            UploadFieldDefinition field = new DynamoUploadFieldDefinition.Builder().withName("same")
                    .withType(UploadFieldType.BOOLEAN).withRequired(false).build();
            oldFieldDefList.add(field);
            newFieldDefList.add(field);
        }

        // added field
        {
            UploadFieldDefinition field = new DynamoUploadFieldDefinition.Builder().withName("added")
                    .withType(UploadFieldType.BOOLEAN).withRequired(false).build();
            // not added to old
            newFieldDefList.add(field);
        }

        // removed field
        {
            UploadFieldDefinition field = new DynamoUploadFieldDefinition.Builder().withName("removed")
                    .withType(UploadFieldType.BOOLEAN).withRequired(false).build();
            oldFieldDefList.add(field);
            // not added to new
        }

        // multi-choice
        {
            UploadFieldDefinition oldField = new DynamoUploadFieldDefinition.Builder().withName("multi-choice")
                    .withType(UploadFieldType.MULTI_CHOICE).withRequired(false)
                    .withMultiChoiceAnswerList("same", "removed").withAllowOtherChoices(true).build();
            oldFieldDefList.add(oldField);

            UploadFieldDefinition newField = new DynamoUploadFieldDefinition.Builder().withName("multi-choice")
                    .withType(UploadFieldType.MULTI_CHOICE).withRequired(false)
                    .withMultiChoiceAnswerList("same", "added").withAllowOtherChoices(false).build();
            newFieldDefList.add(newField);
        }

        // string, maxLength reduced
        {
            UploadFieldDefinition oldField = new DynamoUploadFieldDefinition.Builder().withName("string-with-length")
                    .withType(UploadFieldType.STRING).withRequired(false).withMaxLength(128).build();
            oldFieldDefList.add(oldField);

            UploadFieldDefinition newField = new DynamoUploadFieldDefinition.Builder().withName("string-with-length")
                    .withType(UploadFieldType.STRING).withRequired(false).withMaxLength(24).build();
            newFieldDefList.add(newField);
        }

        // string, bounded to unbounded
        {
            UploadFieldDefinition oldField = new DynamoUploadFieldDefinition.Builder().withName("unbounded-string")
                    .withType(UploadFieldType.STRING).withRequired(false).withUnboundedText(true).build();
            oldFieldDefList.add(oldField);

            UploadFieldDefinition newField = new DynamoUploadFieldDefinition.Builder().withName("unbounded-string")
                    .withType(UploadFieldType.STRING).withRequired(false).withMaxLength(128).build();
            newFieldDefList.add(newField);
        }

        // sanity check setup
        assertEquals(5, oldFieldDefList.size());
        assertEquals(5, newFieldDefList.size());

        // execute and validate - Removed fields show up last.
        DynamoUploadSchemaDao.MergeSurveySchemaResult mergeResult = DynamoUploadSchemaDao.mergeSurveySchemaFields(
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
        List<UploadFieldDefinition> oldFieldDefList = ImmutableList.of(new DynamoUploadFieldDefinition.Builder()
                .withName("field").withType(UploadFieldType.STRING).withMaxLength(24).build());
        List<UploadFieldDefinition> newFieldDefList = ImmutableList.of(new DynamoUploadFieldDefinition.Builder()
                .withName("field").withType(UploadFieldType.STRING).withUnboundedText(true).build());

        // execute and validate
        DynamoUploadSchemaDao.MergeSurveySchemaResult mergeResult = DynamoUploadSchemaDao.mergeSurveySchemaFields(
                oldFieldDefList, newFieldDefList);
        assertFalse(mergeResult.isSuccess());
        assertEquals(newFieldDefList, mergeResult.getFieldDefinitionList());

    }

    @Test
    public void mergeSurveySchemaFieldsDifferentType() {
        // changing the field type will cause things to break.
        List<UploadFieldDefinition> oldFieldDefList = ImmutableList.of(new DynamoUploadFieldDefinition.Builder()
                .withName("field").withType(UploadFieldType.STRING).withMaxLength(24).build());
        List<UploadFieldDefinition> newFieldDefList = ImmutableList.of(new DynamoUploadFieldDefinition.Builder()
                .withName("field").withType(UploadFieldType.BOOLEAN).build());

        // execute and validate
        DynamoUploadSchemaDao.MergeSurveySchemaResult mergeResult = DynamoUploadSchemaDao.mergeSurveySchemaFields(
                oldFieldDefList, newFieldDefList);
        assertFalse(mergeResult.isSuccess());
        assertEquals(newFieldDefList, mergeResult.getFieldDefinitionList());

    }

    @Test
    public void deleteSchemaByIdAndRev() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        DynamoUploadSchema schemaToDelete = new DynamoUploadSchema();
        ArgumentCaptor<DynamoUploadSchema> loadSchemaArgCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        when(mockMapper.load(loadSchemaArgCaptor.capture())).thenReturn(schemaToDelete);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        dao.deleteUploadSchemaByIdAndRev(new StudyIdentifierImpl("test-study"), "delete-schema", 1);

        // validate intermediate args
        DynamoUploadSchema loadSchemaArg = loadSchemaArgCaptor.getValue();
        assertEquals("test-study", loadSchemaArg.getStudyId());
        assertEquals("delete-schema", loadSchemaArg.getSchemaId());
        assertEquals(1, loadSchemaArg.getRevision());

        // verify delete call
        verify(mockMapper).delete(schemaToDelete);
    }

    @Test
    public void deleteSchemaByIdAndRevNotFound() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUploadSchema> loadSchemaArgCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        when(mockMapper.load(loadSchemaArgCaptor.capture())).thenReturn(null);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        Exception thrownEx = null;
        try {
            dao.deleteUploadSchemaByIdAndRev(new StudyIdentifierImpl("test-study"), "delete-schema", 1);
            fail();
        } catch (EntityNotFoundException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);

        // validate intermediate args
        DynamoUploadSchema loadSchemaArg = loadSchemaArgCaptor.getValue();
        assertEquals("test-study", loadSchemaArg.getStudyId());
        assertEquals("delete-schema", loadSchemaArg.getSchemaId());
        assertEquals(1, loadSchemaArg.getRevision());
    }

    @Test
    public void deleteSchemaById() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);

        PaginatedQueryList<DynamoUploadSchema> mockQueryResult = mock(PaginatedQueryList.class);
        when(mockQueryResult.isEmpty()).thenReturn(false);

        ArgumentCaptor<DynamoDBQueryExpression> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(mockMapper.query(eq(DynamoUploadSchema.class), queryCaptor.capture())).thenReturn(mockQueryResult);

        when(mockMapper.batchDelete(mockQueryResult)).thenReturn(ImmutableList.<DynamoDBMapper.FailedBatch>of());

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        dao.deleteUploadSchemaById(new StudyIdentifierImpl("test-study"), "delete-schema");

        // validate intermediate args
        DynamoDBQueryExpression<DynamoUploadSchema> query = queryCaptor.getValue();
        DynamoUploadSchema queryKey = query.getHashKeyValues();
        assertEquals("test-study", queryKey.getStudyId());
        assertEquals("delete-schema", queryKey.getSchemaId());
    }

    @Test
    public void deleteSchemaByIdNotFound() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);

        PaginatedQueryList<DynamoUploadSchema> mockQueryResult = mock(PaginatedQueryList.class);
        when(mockQueryResult.isEmpty()).thenReturn(true);

        ArgumentCaptor<DynamoDBQueryExpression> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(mockMapper.query(eq(DynamoUploadSchema.class), queryCaptor.capture())).thenReturn(mockQueryResult);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        Exception thrownEx = null;
        try {
            dao.deleteUploadSchemaById(new StudyIdentifierImpl("test-study"), "delete-schema");
            fail();
        } catch (EntityNotFoundException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);

        // validate intermediate args
        DynamoDBQueryExpression<DynamoUploadSchema> query = queryCaptor.getValue();
        DynamoUploadSchema queryKey = query.getHashKeyValues();
        assertEquals("test-study", queryKey.getStudyId());
        assertEquals("delete-schema", queryKey.getSchemaId());
    }

    @Test
    public void deleteSchemaByIdMapperException() {
        // mock failed batch
        // we have to mock extra stuff because BridgeUtils.ifFailuresThrowException() checks all these things
        DynamoDBMapper.FailedBatch failure = new DynamoDBMapper.FailedBatch();
        failure.setException(new Exception("dummy exception message"));
        failure.setUnprocessedItems(ImmutableMap.<String, List<WriteRequest>>of());

        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);

        PaginatedQueryList<DynamoUploadSchema> mockQueryResult = mock(PaginatedQueryList.class);
        when(mockQueryResult.isEmpty()).thenReturn(false);

        ArgumentCaptor<DynamoDBQueryExpression> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(mockMapper.query(eq(DynamoUploadSchema.class), queryCaptor.capture())).thenReturn(mockQueryResult);

        when(mockMapper.batchDelete(mockQueryResult)).thenReturn(ImmutableList.of(failure));

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        Exception thrownEx = null;
        try {
            dao.deleteUploadSchemaById(new StudyIdentifierImpl("test-study"), "delete-schema");
            fail();
        } catch (BridgeServiceException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);

        // validate intermediate args
        DynamoDBQueryExpression<DynamoUploadSchema> query = queryCaptor.getValue();
        DynamoUploadSchema queryKey = query.getHashKeyValues();
        assertEquals("test-study", queryKey.getStudyId());
        assertEquals("delete-schema", queryKey.getSchemaId());
    }

    @Test
    public void testGetSchema() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = setupMockMapperWithSchema(makeUploadSchema("getStudy", "testSchema", 1));

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        DynamoUploadSchema retVal = (DynamoUploadSchema) dao.getUploadSchema("getStudy", "testSchema");

        // Validate call to DDB - we can't compare if the captured argument is equal to the passed in upload
        // schema since DDB objects are mutable.
        assertEquals("getStudy", retVal.getStudyId());
        assertEquals("testSchema", retVal.getSchemaId());
        assertEquals(1, retVal.getRevision());
    }

    @Test(expected = EntityNotFoundException.class)
    public void schemaNotFound() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = setupMockMapperWithSchema(null);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        dao.getUploadSchema("getStudy", "testSchema");
    }

    @Test
    public void getSchemaByIdAndRev() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        DynamoUploadSchema schema = new DynamoUploadSchema();
        ArgumentCaptor<DynamoUploadSchema> loadSchemaArgCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        when(mockMapper.load(loadSchemaArgCaptor.capture())).thenReturn(schema);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        UploadSchema retVal = dao.getUploadSchemaByIdAndRev(new StudyIdentifierImpl("test-study"), "test-schema", 1);
        assertSame(schema, retVal);

        // validate intermediate args
        DynamoUploadSchema loadSchemaArg = loadSchemaArgCaptor.getValue();
        assertEquals("test-study", loadSchemaArg.getStudyId());
        assertEquals("test-schema", loadSchemaArg.getSchemaId());
        assertEquals(1, loadSchemaArg.getRevision());
    }

    @Test
    public void getSchemaByIdAndRevNotFound() {
        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUploadSchema> loadSchemaArgCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        when(mockMapper.load(loadSchemaArgCaptor.capture())).thenReturn(null);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        Exception thrownEx = null;
        try {
            dao.getUploadSchemaByIdAndRev(new StudyIdentifierImpl("test-study"), "test-schema", 1);
            fail();
        } catch (EntityNotFoundException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);

        // validate intermediate args
        DynamoUploadSchema loadSchemaArg = loadSchemaArgCaptor.getValue();
        assertEquals("test-study", loadSchemaArg.getStudyId());
        assertEquals("test-schema", loadSchemaArg.getSchemaId());
        assertEquals(1, loadSchemaArg.getRevision());
    }

    @Test
    public void getUploadSchemasForStudy() {
        // mock result. Create four upload schemas with two different IDs with two revisions each. Should 
        // see the highest of each revision returned. (AAA=2 and BBB=3).
        List<UploadSchema> mockResult = ImmutableList.<UploadSchema>of(
            makeUploadSchema("test-study", "AAA", 1), 
            makeUploadSchema("test-study", "AAA", 2),
            makeUploadSchema("test-study", "BBB", 2),
            makeUploadSchema("test-study", "BBB", 3));

        // mock study ID index
        DynamoIndexHelper mockStudyIdIndex = mock(DynamoIndexHelper.class);
        when(mockStudyIdIndex.query(UploadSchema.class, "studyId", "test-study", null)).thenReturn(mockResult);

        // set up test dao
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setStudyIdIndex(mockStudyIdIndex);

        // execute and validate
        List<UploadSchema> outputSchemaList = dao.getUploadSchemasForStudy(new StudyIdentifierImpl("test-study"));
        
        assertEquals(2, outputSchemaList.size());
        
        assertEquals("AAA", outputSchemaList.get(0).getSchemaId());
        assertEquals(2, outputSchemaList.get(0).getRevision());
        
        assertEquals("BBB", outputSchemaList.get(1).getSchemaId());
        assertEquals(3, outputSchemaList.get(1).getRevision());
    }

    @Test
    public void getUploadSchemasForStudyWithOneSchema() {
        // mock result. 
        List<UploadSchema> mockResult = ImmutableList.<UploadSchema>of(new DynamoUploadSchema());

        // mock study ID index
        DynamoIndexHelper mockStudyIdIndex = mock(DynamoIndexHelper.class);
        when(mockStudyIdIndex.query(UploadSchema.class, "studyId", "test-study", null)).thenReturn(mockResult);

        // set up test dao
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setStudyIdIndex(mockStudyIdIndex);

        // execute and validate
        List<UploadSchema> outputSchemaList = dao.getUploadSchemasForStudy(new StudyIdentifierImpl("test-study"));
        // No longer assertSame, as the DAO filters the list it gets from DDB mapper.
        assertEquals(mockResult, outputSchemaList);
    }
    
    @Test
    public void getUploadSchemasAllRevisions() {
        DynamoDBMapper mockMapper = setupMockMapperWithListResults();

        // set up test dao
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);

        // execute and validate
        List<UploadSchema> outputSchemaList = dao.getUploadSchemaAllRevisions(new StudyIdentifierImpl("test-study"), "AAA");

        assertEquals(3, outputSchemaList.size());
        assertEquals(3, outputSchemaList.get(0).getRevision());
        assertEquals(2, outputSchemaList.get(1).getRevision());
        assertEquals(1, outputSchemaList.get(2).getRevision());
    }

    @Test
    public void updateV4IncompatibleChange() {
        // This update fails for 4 reasons
        // - deleted fields
        // - modified non-compatible field
        // - added required fields
        // - modified schema type

        // Make old schema - Only field def list and schema type matter for this test.
        List<UploadFieldDefinition> oldFieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("always").withType(UploadFieldType.BOOLEAN).build(),
                new DynamoUploadFieldDefinition.Builder().withName("delete-me-1").withType(UploadFieldType.BOOLEAN)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("delete-me-2").withType(UploadFieldType.INT)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("modify-me-1").withType(UploadFieldType.STRING)
                        .withUnboundedText(true).build(),
                new DynamoUploadFieldDefinition.Builder().withName("modify-me-2").withType(UploadFieldType.STRING)
                        .withUnboundedText(true).build());

        DynamoUploadSchema oldSchema = new DynamoUploadSchema();
        oldSchema.setSchemaType(UploadSchemaType.IOS_DATA);
        oldSchema.setFieldDefinitions(oldFieldDefList);

        // Make new schema
        List<UploadFieldDefinition> newFieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("always").withType(UploadFieldType.BOOLEAN).build(),
                new DynamoUploadFieldDefinition.Builder().withName("modify-me-1").withType(UploadFieldType.STRING)
                        .withMaxLength(24).build(),
                new DynamoUploadFieldDefinition.Builder().withName("modify-me-2").withType(UploadFieldType.FLOAT)
                        .withMaxLength(24).build(),
                new DynamoUploadFieldDefinition.Builder().withName("add-me-1").withType(UploadFieldType.BOOLEAN)
                        .withRequired(true).build(),
                new DynamoUploadFieldDefinition.Builder().withName("add-me-2").withType(UploadFieldType.BOOLEAN)
                        .withRequired(true).build());

        DynamoUploadSchema newSchema = new DynamoUploadSchema();
        newSchema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        newSchema.setFieldDefinitions(newFieldDefList);

        // set up mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUploadSchema> getSchemaKeyCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        when(mockMapper.load(getSchemaKeyCaptor.capture())).thenReturn(oldSchema);

        // set up dao
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);

        // execute and validate
        String errMsg = null;
        try {
            dao.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV, newSchema);
            fail("expected exception");
        } catch (BadRequestException ex) {
            errMsg = ex.getMessage();
        }

        assertTrue(errMsg.contains("Added fields must be optional: add-me-1, add-me-2"));
        assertTrue(errMsg.contains("Can't delete fields: delete-me-1, delete-me-2"));
        assertTrue(errMsg.contains("Incompatible changes to fields: modify-me-1, modify-me-2"));
        assertTrue(errMsg.contains("Can't modify schema type, old=IOS_DATA, new=IOS_SURVEY"));

        // validate calls to DDB
        DynamoUploadSchema getSchemaKey = getSchemaKeyCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, getSchemaKey.getStudyId());
        assertEquals(SCHEMA_ID, getSchemaKey.getSchemaId());
        assertEquals(SCHEMA_REV, getSchemaKey.getRevision());

        verify(mockMapper, never()).save(any());
    }

    @Test
    public void updateV4Success() {
        // Test update with
        // - unchanged field
        // - modified compatible field
        // - added optional field

        // Make old schema - Only field def list and schema type matter for this test.
        List<UploadFieldDefinition> oldFieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("always").withType(UploadFieldType.BOOLEAN).build(),
                new DynamoUploadFieldDefinition.Builder().withName("modify-me").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("foo", "bar").withAllowOtherChoices(false).build());

        DynamoUploadSchema oldSchema = new DynamoUploadSchema();
        oldSchema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        oldSchema.setFieldDefinitions(oldFieldDefList);

        // Make new schema
        List<UploadFieldDefinition> newFieldDefList = ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("always").withType(UploadFieldType.BOOLEAN).build(),
                new DynamoUploadFieldDefinition.Builder().withName("modify-me").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("foo", "bar", "baz").withAllowOtherChoices(true).build(),
                new DynamoUploadFieldDefinition.Builder().withName("added-optional-field")
                        .withType(UploadFieldType.BOOLEAN).withRequired(false).build());

        DynamoUploadSchema newSchema = new DynamoUploadSchema();
        newSchema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        newSchema.setFieldDefinitions(newFieldDefList);

        // set up mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<DynamoUploadSchema> getSchemaKeyCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        when(mockMapper.load(getSchemaKeyCaptor.capture())).thenReturn(oldSchema);

        // set up dao
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);

        // execute and validate
        UploadSchema updatedSchema = dao.updateSchemaRevisionV4(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV,
                newSchema);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, updatedSchema.getStudyId());
        assertEquals(SCHEMA_ID, updatedSchema.getSchemaId());
        assertEquals(SCHEMA_REV, updatedSchema.getRevision());
        assertEquals(UploadSchemaType.IOS_SURVEY, updatedSchema.getSchemaType());
        assertEquals(newFieldDefList, updatedSchema.getFieldDefinitions());

        // validate calls to DDB
        DynamoUploadSchema getSchemaKey = getSchemaKeyCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, getSchemaKey.getStudyId());
        assertEquals(SCHEMA_ID, getSchemaKey.getSchemaId());
        assertEquals(SCHEMA_REV, getSchemaKey.getRevision());

        verify(mockMapper).save(same(updatedSchema));
    }

    private static DynamoDBMapper setupMockMapperWithSchema(DynamoUploadSchema schema) {
        // mock get result
        PaginatedQueryList<DynamoUploadSchema> mockGetResult = mock(PaginatedQueryList.class);
        if (schema != null) {
            // mock result should contain the old rev
            when(mockGetResult.isEmpty()).thenReturn(false);
            when(mockGetResult.get(0)).thenReturn(schema);
        } else {
            // no old rev means no old result
            when(mockGetResult.isEmpty()).thenReturn(true);
        }

        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        when(mockMapper.query(
            (Class<DynamoUploadSchema>)eq(DynamoUploadSchema.class), 
            (DynamoDBQueryExpression<DynamoUploadSchema>)notNull(DynamoDBQueryExpression.class)
        )).thenReturn(mockGetResult);
        return mockMapper;
    }
    
    private DynamoDBMapper setupMockMapperWithListResults() {
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        
        // Reverse order, as queried
        List<DynamoUploadSchema> results = ImmutableList.of(
            makeUploadSchema("test-study", "AAA", 3), 
            makeUploadSchema("test-study", "AAA", 2), 
            makeUploadSchema("test-study", "AAA", 1));
        
        final PaginatedQueryList<DynamoUploadSchema> queryResults = (PaginatedQueryList<DynamoUploadSchema>)mock(PaginatedQueryList.class);
        when(queryResults.iterator()).thenReturn(results.iterator());
        when(queryResults.toArray()).thenReturn(results.toArray());
        when(mockMapper.query(
            (Class<DynamoUploadSchema>)any(Class.class), 
            (DynamoDBQueryExpression<DynamoUploadSchema>)any(DynamoDBQueryExpression.class)
        )).thenReturn(queryResults);
        return mockMapper;
    }
    
    private static DynamoUploadSchema makeUploadSchema(String studyId, String schemaId, int rev) {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId(studyId);
        ddbUploadSchema.setSchemaId(schemaId);
        ddbUploadSchema.setRevision(rev);
        return ddbUploadSchema;
    }
}
