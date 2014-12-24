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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillStatus;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoBackfillDaoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private DynamoBackfillDao backfillDao;

    @Before
    public void before() {
        DynamoInitializer.init(DynamoBackfillTask.class, DynamoBackfillRecord.class);
        DynamoTestUtil.clearTable(DynamoBackfillTask.class);
        DynamoTestUtil.clearTable(DynamoBackfillRecord.class);
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoBackfillTask.class);
        DynamoTestUtil.clearTable(DynamoBackfillRecord.class);
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
        List<? extends BackfillTask> tasks = backfillDao.getTasks("name",
                DateTime.now(DateTimeZone.UTC).getMillis() - 1000L);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        // Update
        backfillDao.updateTaskStatus(task.getId(), BackfillStatus.COMPLETED);
        task = backfillDao.getTask(task.getId());
        assertEquals(BackfillStatus.COMPLETED.name(), task.getStatus());
    }

    @Test
    public void testRecord() throws Exception {
        // Create
        final long timestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        assertEquals(0, backfillDao.getRecordCount("task1"));
        BackfillRecord record = backfillDao.createRecord("task1", "study1", "account1", "op1");
        assertEquals(1, backfillDao.getRecordCount("task1"));
        assertEquals("task1", record.getTaskId());
        assertTrue(record.getTimestamp() >= timestamp);
        JsonNode json = MAPPER.readTree(record.getRecord());
        assertEquals("study1", json.get("study").asText());
        assertEquals("account1", json.get("account").asText());
        assertEquals("op1", json.get("operation").asText());
        // Create a 2nd record
        backfillDao.createRecord("task1", "study1", "account2", "op2");
        assertEquals(2, backfillDao.getRecordCount("task1"));
        // Create record in a different study
        backfillDao.createRecord("task3", "study3", "account3", "op3");
        assertEquals(2, backfillDao.getRecordCount("task1"));
        assertEquals(1, backfillDao.getRecordCount("task2"));
        // Test iterator
        Iterator<? extends BackfillRecord> iterator = backfillDao.getRecords("task1");
        assertTrue(iterator.hasNext());
        BackfillRecord record1 = iterator.next();
        assertEquals("task1", record1.getTaskId());
        json = MAPPER.readTree(record1.getRecord());
        assertEquals("study1", json.get("study").asText());
        assertEquals("account1", json.get("account").asText());
        assertEquals("op1", json.get("operation").asText());
        assertTrue(iterator.hasNext());
        BackfillRecord record2 = iterator.next();
        assertEquals("task1", record2.getTaskId());
        json = MAPPER.readTree(record2.getRecord());
        assertEquals("study1", json.get("study").asText());
        assertEquals("account2", json.get("account").asText());
        assertEquals("op2", json.get("operation").asText());
        assertFalse(iterator.hasNext());
        iterator = backfillDao.getRecords("task3");
        assertTrue(iterator.hasNext());
        BackfillRecord record3 = iterator.next();
        assertEquals("task3", record3.getTaskId());
        json = MAPPER.readTree(record3.getRecord());
        assertEquals("study3", json.get("study").asText());
        assertEquals("account3", json.get("account").asText());
        assertEquals("op3", json.get("operation").asText());
        assertFalse(iterator.hasNext());
    }
}
