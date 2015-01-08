package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

@SuppressWarnings("unchecked")
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
        // mock get result
        PaginatedQueryList<DynamoUploadSchema> mockGetResult = mock(PaginatedQueryList.class);
        if (oldRev != null) {
            // mock result should contain the old rev
            when(mockGetResult.isEmpty()).thenReturn(false);
            when(mockGetResult.get(0)).thenReturn(makeUploadSchema(studyId, schemaId, oldRev));
        } else {
            // no old rev means no old result
            when(mockGetResult.isEmpty()).thenReturn(true);
        }

        // mock DDB mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        when(mockMapper.query(eq(DynamoUploadSchema.class), notNull(DynamoDBQueryExpression.class))).thenReturn(
                mockGetResult);

        // set up test and execute
        DynamoUploadSchemaDao dao = new DynamoUploadSchemaDao();
        dao.setDdbMapper(mockMapper);
        UploadSchema retVal = dao.createOrUpdateUploadSchema(studyId, schemaId, makeUploadSchema(studyId, schemaId,
                newRev));

        // Validate call to DDB - we can't compare if the captured argument is equal to the passed in upload
        // schema since DDB objects are mutable.
        ArgumentCaptor<DynamoUploadSchema> arg = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        verify(mockMapper).save(arg.capture(), notNull(DynamoDBSaveExpression.class));
        assertEquals(studyId, arg.getValue().getStudyId());
        assertEquals(schemaId, arg.getValue().getSchemaId());

        // DAO auto-increments the revision
        assertEquals(newRev + 1, arg.getValue().getRevision());

        // The retVal should be the same object as the schema was sent to DDB.
        assertSame(arg.getValue(), retVal);
    }

    // TODO get schema

    // TODO schema not found

    private static DynamoUploadSchema makeUploadSchema(String studyId, String schemaId, int rev) {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId(studyId);
        ddbUploadSchema.setSchemaId(schemaId);
        ddbUploadSchema.setRevision(rev);
        return ddbUploadSchema;
    }
}
