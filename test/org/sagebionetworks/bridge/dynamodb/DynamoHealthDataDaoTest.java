package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class DynamoHealthDataDaoTest {
    private static final String TEST_HEALTH_CODE = "1234";
    private static final long TEST_CREATED_ON = 1427970429000L;
    private static final long TEST_CREATED_ON_END = 1427970471979L;
    private static final String TEST_SCHEMA_ID = "api";

    @Test
    public void createOrUpdateRecord() {
        // mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setMapper(mockMapper);

        // execute
        String id = dao.createOrUpdateRecord(new DynamoHealthDataRecord());

        // validate that the returned ID matches the ID received by the DDB mapper
        ArgumentCaptor<DynamoHealthDataRecord> arg = ArgumentCaptor.forClass(DynamoHealthDataRecord.class);
        verify(mockMapper).save(arg.capture());
        assertEquals(id, arg.getValue().getId());
    }

    @Test
    public void deleteRecordsForHealthCode() {
        // mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<List> arg = ArgumentCaptor.forClass(List.class);
        when(mockMapper.batchDelete(arg.capture())).thenReturn(Collections.<DynamoDBMapper.FailedBatch>emptyList());

        // mock index helper
        ItemCollection mockItemCollection = mock(ItemCollection.class);
        IteratorSupport mockIteratorSupport = mock(IteratorSupport.class);
        Item mockItem = new Item().with("healthCode", "test health code").with("id", "test ID");
        
        when(mockItemCollection.iterator()).thenReturn(mockIteratorSupport);
        when(mockIteratorSupport.hasNext()).thenReturn(true, false);
        when(mockIteratorSupport.next()).thenReturn(mockItem);
        
        DynamoIndexHelper mockIndexHelper = mock(DynamoIndexHelper.class);
        Index mockIndex = mock(Index.class);
        when(mockIndexHelper.getIndex()).thenReturn(mockIndex);
        when(mockIndex.query("healthCode", "test health code"))
                .thenReturn(mockItemCollection);
        
        // set up and execute
        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setMapper(mockMapper);
        dao.setHealthCodeIndex(mockIndexHelper);
        int numDeleted = dao.deleteRecordsForHealthCode("test health code");
        assertEquals(1, numDeleted);

        // validate intermediate results
        List<HealthDataRecord> recordKeyList = arg.getValue();
        assertEquals(1, recordKeyList.size());
        assertEquals("test ID", recordKeyList.get(0).getId());
    }

    @Test
    public void deleteRecordsForHealthCodeMapperException() {
        // mock failed batch
        // we have to mock extra stuff because BridgeUtils.ifFailuresThrowException() checks all these things
        DynamoDBMapper.FailedBatch failure = new DynamoDBMapper.FailedBatch();
        failure.setException(new Exception("dummy exception message"));
        failure.setUnprocessedItems(Collections.emptyMap());

        // mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        ArgumentCaptor<List> arg = ArgumentCaptor.forClass(List.class);
        when(mockMapper.batchDelete(arg.capture())).thenReturn(Collections.singletonList(failure));

        // mock index helper
        ItemCollection mockItemCollection = mock(ItemCollection.class);
        IteratorSupport mockIteratorSupport = mock(IteratorSupport.class);
        Item mockItem = new Item().with("healthCode", "test health code").with("id", "error record");
        
        when(mockItemCollection.iterator()).thenReturn(mockIteratorSupport);
        when(mockIteratorSupport.hasNext()).thenReturn(true, false);
        when(mockIteratorSupport.next()).thenReturn(mockItem);
        
        DynamoIndexHelper mockIndexHelper = mock(DynamoIndexHelper.class);
        Index mockIndex = mock(Index.class);
        when(mockIndexHelper.getIndex()).thenReturn(mockIndex);
        when(mockIndex.query("healthCode", "test health code"))
                .thenReturn(mockItemCollection);

        // set up
        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setMapper(mockMapper);
        dao.setHealthCodeIndex(mockIndexHelper);

        // execute and validate exception
        Exception thrownEx = null;
        try {
            dao.deleteRecordsForHealthCode("test health code");
            fail();
        } catch (BridgeServiceException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);

        // validate intermediate results
        List<HealthDataRecord> recordKeyList = arg.getValue();
        assertEquals(1, recordKeyList.size());
        assertEquals("error record", recordKeyList.get(0).getId());
    }

    @Test
    public void getRecordsForUploadDate() {
        // mock index helper
        List<HealthDataRecord> mockResult = Collections.emptyList();
        DynamoIndexHelper mockIndex = mock(DynamoIndexHelper.class);
        when(mockIndex.query(HealthDataRecord.class, "uploadDate", "2015-02-11", null)).thenReturn(mockResult);

        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setUploadDateIndex(mockIndex);

        // execute and validate
        List<HealthDataRecord> retVal = dao.getRecordsForUploadDate("2015-02-11");
        assertSame(mockResult, retVal);
    }

    @Test
    public void getRecordsByHealthCodeCreatedOnSchemaId() {
        // Mock mapper with record.
        DynamoHealthDataRecord record = new DynamoHealthDataRecord();
        record.setHealthCode(TEST_HEALTH_CODE);
        record.setId("test ID");
        record.setCreatedOn(TEST_CREATED_ON);
        record.setSchemaId(TEST_SCHEMA_ID);

        QueryResultPage<DynamoHealthDataRecord> resultPage = new QueryResultPage<>();
        resultPage.setResults(ImmutableList.of(record));

        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        when(mockMapper.queryPage(eq(DynamoHealthDataRecord.class), any())).thenReturn(resultPage);

        DynamoHealthDataDao dao = new DynamoHealthDataDao();
        dao.setMapper(mockMapper);

        // Execute and validate.
        List<HealthDataRecord> retVal = dao.getRecordsByHealthCodeCreatedOn(TEST_HEALTH_CODE, TEST_CREATED_ON,
                TEST_CREATED_ON_END);
        assertEquals(1, retVal.size());
        assertSame(record, retVal.get(0));

        // Verify query.
        ArgumentCaptor<DynamoDBQueryExpression> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        verify(mockMapper).queryPage(eq(DynamoHealthDataRecord.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoHealthDataRecord> query = queryCaptor.getValue();
        assertEquals(TEST_HEALTH_CODE, query.getHashKeyValues().getHealthCode());
        assertFalse(query.isConsistentRead());
        assertEquals(BridgeConstants.DUPE_RECORDS_MAX_COUNT, query.getLimit().intValue());

        Condition rangeKeyCondition = query.getRangeKeyConditions().get("createdOn");
        assertEquals(ComparisonOperator.BETWEEN.toString(), rangeKeyCondition.getComparisonOperator());
        assertEquals(String.valueOf(TEST_CREATED_ON), rangeKeyCondition.getAttributeValueList().get(0).getN());
        assertEquals(String.valueOf(TEST_CREATED_ON_END), rangeKeyCondition.getAttributeValueList().get(1).getN());
    }
}
