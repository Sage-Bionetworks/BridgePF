package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import org.joda.time.DateTime;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.schema.UploadSchemaKey;

// Basic tests that test data flow
@SuppressWarnings({ "rawtypes", "unchecked" })
public class DynamoUploadDedupeDaoTest {
    private static final UploadSchemaKey TEST_SCHEMA_KEY = new UploadSchemaKey.Builder().withStudyId("test-study")
            .withSchemaId("test-schema").withRevision(7).build();

    private DynamoUploadDedupeDao dao;
    private DynamoDBMapper mockMapper;
    private ArgumentCaptor<DynamoDBQueryExpression> queryCaptor;

    @Test
    public void testIsDuplicate() {
        // set up and execute
        mockMapperIsDuplicate(3);
        assertTrue(dao.isDuplicate(42000L, "test-health-code", TEST_SCHEMA_KEY));

        // validate that we're passing in the right query
        DynamoDBQueryExpression<DynamoUploadDedupe> query = queryCaptor.getValue();

        // validate hash key
        DynamoUploadDedupe queryHashKey = query.getHashKeyValues();
        assertEquals("test-health-code:test-study-test-schema-v7", queryHashKey.getDdbKey());

        // validate range key
        Map<String, Condition> rangeKeyConditionMap = query.getRangeKeyConditions();
        assertEquals(1, rangeKeyConditionMap.size());

        Condition rangeKeyCondition = rangeKeyConditionMap.get("createdOn");
        assertEquals(ComparisonOperator.BETWEEN.name(), rangeKeyCondition.getComparisonOperator());

        List<AttributeValue> conditionValueList = rangeKeyCondition.getAttributeValueList();
        assertEquals(2, conditionValueList.size());
        assertEquals("41000", conditionValueList.get(0).getN());
        assertEquals("43000", conditionValueList.get(1).getN());
    }

    @Test
    public void testIsNotDuplicate() {
        // set up and execute
        mockMapperIsDuplicate(0);
        assertFalse(dao.isDuplicate(42000L, "test-health-code", TEST_SCHEMA_KEY));

        // We already validate the query in the previous test. No need to duplicate it here.
    }

    private void mockMapperIsDuplicate(int numDupes) {
        mockMapper = mock(DynamoDBMapper.class);
        queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(mockMapper.count(eq(DynamoUploadDedupe.class), queryCaptor.capture())).thenReturn(numDupes);

        dao = new DynamoUploadDedupeDao();
        dao.setMapper(mockMapper);
    }

    @Test
    public void registerUpload() {
        // set up dao and mock mapper
        mockMapper = mock(DynamoDBMapper.class);
        dao = new DynamoUploadDedupeDao();
        dao.setMapper(mockMapper);

        // execute
        long createdOnMillis = DateTime.parse("2016-01-12T01:00-0800").getMillis();
        dao.registerUpload(createdOnMillis, "test-health-code", TEST_SCHEMA_KEY, "test-upload");

        // verify DDB save
        ArgumentCaptor<DynamoUploadDedupe> ddbObjCaptor = ArgumentCaptor.forClass(DynamoUploadDedupe.class);
        verify(mockMapper).save(ddbObjCaptor.capture());
        DynamoUploadDedupe ddbObj = ddbObjCaptor.getValue();
        assertEquals("2016-01-12", ddbObj.getCreatedDate().toString());
        assertEquals(createdOnMillis, ddbObj.getCreatedOn());
        assertEquals("test-health-code", ddbObj.getHealthCode());
        assertEquals("test-study-test-schema-v7", ddbObj.getSchemaKey());
        assertEquals("test-upload", ddbObj.getUploadId());
    }
}
