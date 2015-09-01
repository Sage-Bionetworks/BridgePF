package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.notNull;
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
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class DynamoUploadSchemaDaoTest {
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

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        UploadSchema retVal = dao.createOrUpdateUploadSchema(studyId, makeUploadSchema(null, schemaId, newRev));

        // Validate call to DDB - we can't compare if the captured argument is equal to the passed in upload
        // schema since DDB objects are mutable.
        ArgumentCaptor<DynamoUploadSchema> arg = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        verify(mockMapper).save(arg.capture(), notNull(DynamoDBSaveExpression.class));
        assertEquals(schemaId, arg.getValue().getSchemaId());

        // validate that study ID was injected
        assertEquals(studyId, arg.getValue().getStudyId());

        // DAO auto-increments the revision
        assertEquals(newRev + 1, arg.getValue().getRevision());

        // The retVal should be the same object as the schema was sent to DDB.
        assertSame(arg.getValue(), retVal);
    }

    @Test
    public void createUploadSchemaFromSurvey() {
        // create survey questions
        List<SurveyElement> surveyElementList = new ArrayList<>();

        // no constraints
        {
            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("no-constraints");
            q.setConstraints(null);
            surveyElementList.add(q);
        }

        // multi value string
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(true);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("multi-value-string");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // multi value int
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.INTEGER);
            constraints.setAllowMultiple(true);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("multi-value-int");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // multi value string don't allow multiple
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.STRING);
            constraints.setAllowMultiple(false);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("multi-value-single-choice-string");
            q.setConstraints(constraints);
            surveyElementList.add(q);
        }

        // multi value int don't allow multiple
        {
            MultiValueConstraints constraints = new MultiValueConstraints();
            constraints.setDataType(DataType.INTEGER);
            constraints.setAllowMultiple(false);

            SurveyQuestion q = new DynamoSurveyQuestion();
            q.setIdentifier("multi-value-single-choice-int");
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
            q.setIdentifier("decimal");
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

        // make survey
        Survey survey = new DynamoSurvey();
        survey.setIdentifier("test-survey");
        survey.setName("Test Survey");
        survey.setElements(surveyElementList);

        // Mock DDB mapper - This is the new schema case, so setting up the mock mapper with null is fine.
        DynamoDBMapper mockMapper = setupMockMapperWithSchema(null);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        DynamoUploadSchema createdSchema = (DynamoUploadSchema) dao.createUploadSchemaFromSurvey(
                new StudyIdentifierImpl("survey-study"), survey);

        // validate schema
        assertEquals("Test Survey", createdSchema.getName());
        assertEquals(1, createdSchema.getRevision());
        assertEquals("test-survey", createdSchema.getSchemaId());
        assertEquals(UploadSchemaType.IOS_SURVEY, createdSchema.getSchemaType());
        assertEquals("survey-study", createdSchema.getStudyId());

        List<UploadFieldDefinition> fieldDefList = createdSchema.getFieldDefinitions();
        assertEquals(15, fieldDefList.size());

        assertEquals("no-constraints", fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.INLINE_JSON_BLOB, fieldDefList.get(0).getType());

        assertEquals("multi-value-string", fieldDefList.get(1).getName());
        assertEquals(UploadFieldType.ATTACHMENT_JSON_BLOB, fieldDefList.get(1).getType());

        assertEquals("multi-value-int", fieldDefList.get(2).getName());
        assertEquals(UploadFieldType.INLINE_JSON_BLOB, fieldDefList.get(2).getType());

        assertEquals("multi-value-single-choice-string", fieldDefList.get(3).getName());
        assertEquals(UploadFieldType.INLINE_JSON_BLOB, fieldDefList.get(3).getType());

        assertEquals("multi-value-single-choice-int", fieldDefList.get(4).getName());
        assertEquals(UploadFieldType.INLINE_JSON_BLOB, fieldDefList.get(4).getType());

        assertEquals("duration", fieldDefList.get(5).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(5).getType());

        assertEquals("unbounded-string", fieldDefList.get(6).getName());
        assertEquals(UploadFieldType.ATTACHMENT_BLOB, fieldDefList.get(6).getType());

        assertEquals("long-string", fieldDefList.get(7).getName());
        assertEquals(UploadFieldType.ATTACHMENT_BLOB, fieldDefList.get(7).getType());

        assertEquals("short-string", fieldDefList.get(8).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(8).getType());

        assertEquals("int", fieldDefList.get(9).getName());
        assertEquals(UploadFieldType.INT, fieldDefList.get(9).getType());

        assertEquals("decimal", fieldDefList.get(10).getName());
        assertEquals(UploadFieldType.FLOAT, fieldDefList.get(10).getType());

        assertEquals("boolean", fieldDefList.get(11).getName());
        assertEquals(UploadFieldType.BOOLEAN, fieldDefList.get(11).getType());

        assertEquals("calendar-date", fieldDefList.get(12).getName());
        assertEquals(UploadFieldType.CALENDAR_DATE, fieldDefList.get(12).getType());

        assertEquals("local-time", fieldDefList.get(13).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(13).getType());

        assertEquals("timestamp", fieldDefList.get(14).getName());
        assertEquals(UploadFieldType.TIMESTAMP, fieldDefList.get(14).getType());

        // Validate call to DDB - make sure returned schema same as the one sent to DDB.
        ArgumentCaptor<DynamoUploadSchema> arg = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        verify(mockMapper).save(arg.capture(), notNull(DynamoDBSaveExpression.class));
        assertSame(createdSchema, arg.getValue());
    }

    @Test
    public void updateUploadSchemaFromSurvey() {
        // create survey questions - We already tested all the survey question types, so we don't need a big huge list.
        SurveyQuestion q = new DynamoSurveyQuestion();
        q.setIdentifier("one-more-question");
        q.setConstraints(new IntegerConstraints());
        List<SurveyElement> surveyElementList = ImmutableList.<SurveyElement>of(q);

        // make survey
        Survey survey = new DynamoSurvey();
        survey.setIdentifier("test-survey");
        survey.setName("Test Survey (Updated)");
        survey.setElements(surveyElementList);

        // old schema - has different questions
        DynamoUploadSchema oldSchema = makeUploadSchema("survey-study", "test-survey", 2);
        oldSchema.setFieldDefinitions(ImmutableList.of(new DynamoUploadFieldDefinition.Builder()
                .withName("different-question").withType(UploadFieldType.STRING).build()));

        // Mock DDB mapper - This is only used for rev, so we don't need a fully fleshed out schema.
        DynamoDBMapper mockMapper = setupMockMapperWithSchema(oldSchema);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        DynamoUploadSchema createdSchema = (DynamoUploadSchema) dao.createUploadSchemaFromSurvey(
                new StudyIdentifierImpl("survey-study"), survey);

        // validate schema
        assertEquals("Test Survey (Updated)", createdSchema.getName());
        assertEquals(3, createdSchema.getRevision());
        assertEquals("test-survey", createdSchema.getSchemaId());
        assertEquals(UploadSchemaType.IOS_SURVEY, createdSchema.getSchemaType());
        assertEquals("survey-study", createdSchema.getStudyId());

        List<UploadFieldDefinition> fieldDefList = createdSchema.getFieldDefinitions();
        assertEquals(1, fieldDefList.size());
        assertEquals("one-more-question", fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.INT, fieldDefList.get(0).getType());

        // Validate call to DDB - make sure returned schema same as the one sent to DDB.
        ArgumentCaptor<DynamoUploadSchema> arg = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        verify(mockMapper).save(arg.capture(), notNull(DynamoDBSaveExpression.class));
        assertSame(createdSchema, arg.getValue());
    }

    @Test
    public void updateUploadSchemaFromSurveySameFields() {
        // create survey questions - We already tested all the survey question types, so we don't need a big huge list.
        SurveyQuestion q1 = new DynamoSurveyQuestion();
        q1.setIdentifier("foo-question");
        q1.setConstraints(new BooleanConstraints());

        SurveyQuestion q2 = new DynamoSurveyQuestion();
        q2.setIdentifier("bar-question");
        q2.setConstraints(new IntegerConstraints());

        List<SurveyElement> surveyElementList = ImmutableList.<SurveyElement>of(q1, q2);

        // make survey
        Survey survey = new DynamoSurvey();
        survey.setIdentifier("same-survey");
        survey.setName("Test Survey (Same Fields)");
        survey.setElements(surveyElementList);

        // old schema - has same questions, but in the opposite order. (This should still be treated as same schema
        // fields.)
        DynamoUploadSchema oldSchema = makeUploadSchema("survey-study", "same-survey", 4);
        oldSchema.setName("Old Name");
        oldSchema.setFieldDefinitions(ImmutableList.of(
                new DynamoUploadFieldDefinition.Builder().withName("bar-question").withType(UploadFieldType.INT)
                        .build(),
                new DynamoUploadFieldDefinition.Builder().withName("foo-question").withType(UploadFieldType.BOOLEAN)
                        .build()));

        // Mock DDB mapper - This is only used for rev, so we don't need a fully fleshed out schema.
        DynamoDBMapper mockMapper = setupMockMapperWithSchema(oldSchema);

        // set up test dao and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        DynamoUploadSchema createdSchema = (DynamoUploadSchema) dao.createUploadSchemaFromSurvey(
                new StudyIdentifierImpl("survey-study"), survey);

        // validate schema - it should be the same as the old one
        assertSame(oldSchema, createdSchema);

        // Make sure we don't save anything to DDB.
        verify(mockMapper, never()).save(any());
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
        // mock result
        List<UploadSchema> mockResult = ImmutableList.<UploadSchema>of(new DynamoUploadSchema());

        // mock study ID index
        DynamoIndexHelper mockStudyIdIndex = mock(DynamoIndexHelper.class);
        when(mockStudyIdIndex.query(UploadSchema.class, "studyId", "test-study")).thenReturn(mockResult);

        // set up test dao
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setStudyIdIndex(mockStudyIdIndex);

        // execute and validate
        List<UploadSchema> outputSchemaList = dao.getUploadSchemasForStudy(new StudyIdentifierImpl("test-study"));
        assertSame(mockResult, outputSchemaList);
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

    private static DynamoUploadSchema makeUploadSchema(String studyId, String schemaId, int rev) {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId(studyId);
        ddbUploadSchema.setSchemaId(schemaId);
        ddbUploadSchema.setRevision(rev);
        return ddbUploadSchema;
    }
}
