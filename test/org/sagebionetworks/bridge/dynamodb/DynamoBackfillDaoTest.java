package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.models.backfill.BackfillRecord;
import org.sagebionetworks.bridge.models.backfill.BackfillStatus;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoBackfillDaoTest {

    private DynamoDBMapper taskMapper;
    private DynamoDBMapper recordMapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client, DynamoNamingHelper dynamoNamingHelper) {
        DynamoDBMapperConfig taskMapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(dynamoNamingHelper.getTableNameOverride(DynamoBackfillTask.class)).build();
        taskMapper = new DynamoDBMapper(client, taskMapperConfig);
        DynamoDBMapperConfig recordMapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(dynamoNamingHelper.getTableNameOverride(DynamoBackfillRecord.class)).build();
        recordMapper = new DynamoDBMapper(client, recordMapperConfig);
    }
    
    @Resource
    private DynamoBackfillDao backfillDao;
    
    private List<BackfillTask> tasksToDelete = Lists.newArrayList();
    
    private List<BackfillRecord> recordsToDelete = Lists.newArrayList();

    @After
    public void after() {
        for (BackfillTask task : tasksToDelete) {
            taskMapper.delete(task);
        }
        for (BackfillRecord record : recordsToDelete) {
            recordMapper.delete(record);
        }
    }

    @Test
    public void testTask() {
        // Create
        BackfillTask task = backfillDao.createTask("name", "user");
        assertNotNull(task);
        assertEquals("name", task.getName());
        assertEquals("user", task.getUser());
        assertTrue(task.getTimestamp() > 0);
        assertNotNull(task.getId());
        assertTrue(task.getId().startsWith("name:"));
        assertEquals(BackfillStatus.SUBMITTED.name(), task.getStatus());
        // Get
        task = backfillDao.getTask(task.getId());
        assertNotNull(task);
        assertEquals("name", task.getName());
        assertEquals("user", task.getUser());
        assertTrue(task.getTimestamp() > 0);
        assertNotNull(task.getId());
        assertTrue(task.getId().startsWith("name:"));
        assertEquals(BackfillStatus.SUBMITTED.name(), task.getStatus());
        // Get list
        BackfillTask secondTask = backfillDao.createTask("name", "user2");
        tasksToDelete.add(secondTask);
        
        List<? extends BackfillTask> tasks = backfillDao.getTasks("name",
                DateTime.now(DateTimeZone.UTC).getMillis() - 1000L);
        assertNotNull(tasks);
        assertEquals(2, tasks.size());
        // Make sure getting back the correct order
        assertEquals("user", tasks.get(0).getUser());
        assertEquals("user2", tasks.get(1).getUser());
        // Update
        backfillDao.updateTaskStatus(task.getId(), BackfillStatus.COMPLETED);
        task = backfillDao.getTask(task.getId());
        tasksToDelete.add(task);
        assertEquals(BackfillStatus.COMPLETED.name(), task.getStatus());
    }

    @Test
    public void testRecord() throws Exception {
        // Create
        final long timestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        assertEquals(0, backfillDao.getRecordCount("task1"));
        BackfillRecord record = backfillDao.createRecord("task1", "study1", "account1", "op1");
        recordsToDelete.add(record);
        
        assertEquals(1, backfillDao.getRecordCount("task1"));
        assertEquals("task1", record.getTaskId());
        assertTrue(record.getTimestamp() >= timestamp);
        JsonNode json = record.toJsonNode();
        assertEquals("study1", json.get("study").asText());
        assertEquals("account1", json.get("account").asText());
        assertEquals("op1", json.get("operation").asText());
        // Create a 2nd record
        BackfillRecord secondRecord = backfillDao.createRecord("task1", "study1", "account2", "op2");
        recordsToDelete.add(secondRecord);
        
        assertEquals(2, backfillDao.getRecordCount("task1"));
        // Create record in a different study
        BackfillRecord thirdRecord = backfillDao.createRecord("task3", "study3", "account3", "op3");
        recordsToDelete.add(thirdRecord);
        
        assertEquals(2, backfillDao.getRecordCount("task1"));
        assertEquals(1, backfillDao.getRecordCount("task3"));
        // Test iterator
        Iterator<? extends BackfillRecord> iterator = backfillDao.getRecords("task1");
        assertTrue(iterator.hasNext());
        BackfillRecord record1 = iterator.next();
        assertEquals("task1", record1.getTaskId());
        json = record1.toJsonNode();
        assertEquals("study1", json.get("study").asText());
        assertEquals("account1", json.get("account").asText());
        assertEquals("op1", json.get("operation").asText());
        assertTrue(iterator.hasNext());
        BackfillRecord record2 = iterator.next();
        assertEquals("task1", record2.getTaskId());
        json = record2.toJsonNode();
        assertEquals("study1", json.get("study").asText());
        assertEquals("account2", json.get("account").asText());
        assertEquals("op2", json.get("operation").asText());
        assertFalse(iterator.hasNext());
        iterator = backfillDao.getRecords("task3");
        assertTrue(iterator.hasNext());
        BackfillRecord record3 = iterator.next();
        assertEquals("task3", record3.getTaskId());
        json = record3.toJsonNode();
        assertEquals("study3", json.get("study").asText());
        assertEquals("account3", json.get("account").asText());
        assertEquals("op3", json.get("operation").asText());
        assertFalse(iterator.hasNext());
    }
}
