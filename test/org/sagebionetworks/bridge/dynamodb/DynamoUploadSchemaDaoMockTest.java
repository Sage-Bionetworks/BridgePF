package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

@SuppressWarnings({ "ConstantConditions", "rawtypes", "RedundantCast", "unchecked" })
public class DynamoUploadSchemaDaoMockTest {
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;

    private DynamoDBMapper mapper;
    private DynamoUploadSchema daoInputSchema;
    private DynamoUploadSchemaDao dao;

    @Before
    public void setup() {
        daoInputSchema = new DynamoUploadSchema();
        mapper = mock(DynamoDBMapper.class);
        dao = spy(new DynamoUploadSchemaDao());
        dao.setDdbMapper(mapper);
    }

    @Test
    public void create() {
        // Create a schema with a version, to make sure we clear it.
        daoInputSchema.setVersion(3L);

        // execute
        UploadSchema daoOutputSchema = dao.createSchemaRevision(daoInputSchema);

        // verify mapper call
        ArgumentCaptor<DynamoUploadSchema> mapperInputSchemaCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        verify(mapper).save(mapperInputSchemaCaptor.capture(), any(DynamoDBSaveExpression.class));

        DynamoUploadSchema mapperInputSchema = mapperInputSchemaCaptor.getValue();
        assertNull(mapperInputSchema.getVersion());

        // schema returned by dao is the same one that was sent to the mapper
        assertSame(mapperInputSchema, daoOutputSchema);
    }

    @Test
    public void delete() {
        // mock mapper to return an empty list of failures (all success)
        List<UploadSchema> schemaListToDelete = ImmutableList.of(new DynamoUploadSchema());
        when(mapper.batchDelete(schemaListToDelete)).thenReturn(ImmutableList.of());

        // execute
        dao.deleteUploadSchemas(schemaListToDelete);

        // verify schema was passed to mapper
        verify(mapper).batchDelete(schemaListToDelete);
    }

    @Test
    public void allSchemasAllRevisions() {
        // spy index helper
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of(new DynamoUploadSchema());
        ArgumentCaptor<DynamoUploadSchema> indexHashKeyCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        doReturn(mapperOutputSchemaList).when(dao).indexHelper(eq(DynamoUploadSchemaDao.STUDY_ID_INDEX_NAME),
                indexHashKeyCaptor.capture());

        // execute
        List<UploadSchema> daoOutputSchemaList = dao.getAllUploadSchemasAllRevisions(TestConstants.TEST_STUDY);

        // validate index hash key
        DynamoUploadSchema indexHashKey = indexHashKeyCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, indexHashKey.getStudyId());

        // Verify DAO output
        assertSame(mapperOutputSchemaList, daoOutputSchemaList);
    }

    @Test
    public void getSchemaAllRevisions() {
        // spy query
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of(new DynamoUploadSchema());
        ArgumentCaptor<DynamoDBQueryExpression> mapperQueryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        doReturn(mapperOutputSchemaList).when(dao).queryHelper(mapperQueryCaptor.capture());

        // execute
        List<UploadSchema> daoOutputSchemaList = dao.getUploadSchemaAllRevisionsById(TestConstants.TEST_STUDY,
                SCHEMA_ID);

        // validate query
        DynamoDBQueryExpression<DynamoUploadSchema> mapperQuery = mapperQueryCaptor.getValue();
        assertFalse(mapperQuery.isScanIndexForward());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, mapperQuery.getHashKeyValues().getStudyId());
        assertEquals(SCHEMA_ID, mapperQuery.getHashKeyValues().getSchemaId());

        // Verify DAO output is same as mapper output. We use equals because they are different instances, but contain
        // the same objects.
        assertEquals(mapperOutputSchemaList, daoOutputSchemaList);
    }

    @Test
    public void getSchemaByIdAndRev() {
        // mock DDB mapper
        DynamoUploadSchema mapperOutputSchema = new DynamoUploadSchema();
        ArgumentCaptor<DynamoUploadSchema> mapperInputSchemaCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        when(mapper.load(mapperInputSchemaCaptor.capture())).thenReturn(mapperOutputSchema);

        // set up test dao and execute
        UploadSchema daoOutputSchema = dao.getUploadSchemaByIdAndRevision(TestConstants.TEST_STUDY, SCHEMA_ID,
                SCHEMA_REV);
        assertSame(mapperOutputSchema, daoOutputSchema);

        // validate intermediate args
        DynamoUploadSchema mapperInputSchema = mapperInputSchemaCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, mapperInputSchema.getStudyId());
        assertEquals(SCHEMA_ID, mapperInputSchema.getSchemaId());
        assertEquals(SCHEMA_REV, mapperInputSchema.getRevision());
    }

    @Test
    public void getLatestById() {
        // mock DDB mapper
        DynamoUploadSchema mapperOutputSchema = new DynamoUploadSchema();
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of(mapperOutputSchema);
        QueryResultPage<DynamoUploadSchema> queryResultPage = new QueryResultPage<>();
        queryResultPage.setResults(mapperOutputSchemaList);

        ArgumentCaptor<DynamoDBQueryExpression> mapperQueryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        when(mapper.queryPage(eq(DynamoUploadSchema.class), mapperQueryCaptor.capture())).thenReturn(queryResultPage);

        // execute
        UploadSchema daoOutputSchema = dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SCHEMA_ID);

        // validate query
        DynamoDBQueryExpression<DynamoUploadSchema> mapperQuery = mapperQueryCaptor.getValue();
        assertFalse(mapperQuery.isScanIndexForward());
        assertEquals(1, mapperQuery.getLimit().intValue());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, mapperQuery.getHashKeyValues().getStudyId());
        assertEquals(SCHEMA_ID, mapperQuery.getHashKeyValues().getSchemaId());

        // Verify DAO output
        assertSame(mapperOutputSchema, daoOutputSchema);
    }

    @Test
    public void getLatestByIdNoResult() {
        // mock DDB mapper
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of();
        QueryResultPage<DynamoUploadSchema> queryResultPage = new QueryResultPage<>();
        queryResultPage.setResults(mapperOutputSchemaList);

        when(mapper.queryPage(eq(DynamoUploadSchema.class), any())).thenReturn(queryResultPage);

        // execute and validate result is null
        UploadSchema daoOutputSchema = dao.getUploadSchemaLatestRevisionById(TestConstants.TEST_STUDY, SCHEMA_ID);
        assertNull(daoOutputSchema);

        // query is already validated in getLatestById(). Don't bother checking again.
    }

    @Test
    public void update() {
        // execute (no additional setup required)
        UploadSchema daoOutputSchema = dao.updateSchemaRevision(daoInputSchema);

        // verify mapper call
        ArgumentCaptor<DynamoUploadSchema> mapperInputSchemaCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        verify(mapper).save(mapperInputSchemaCaptor.capture());

        // schema returned by dao is the same one that was sent to the mapper
        DynamoUploadSchema mapperInputSchema = mapperInputSchemaCaptor.getValue();
        assertSame(mapperInputSchema, daoOutputSchema);
    }
}
