package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.sagebionetworks.bridge.models.upload.UploadSchema;

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
        // pass in null for the study ID in the schema, since schemas from callers won't contain the study ID
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
        when(mockMapper.query(eq(DynamoUploadSchema.class), notNull(DynamoDBQueryExpression.class))).thenReturn(
                mockGetResult);
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
