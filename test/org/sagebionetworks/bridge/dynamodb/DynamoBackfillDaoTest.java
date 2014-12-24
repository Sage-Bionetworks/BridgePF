package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.models.BackfillStatus;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoBackfillDaoTest {

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
        BackfillTask task = backfillDao.createTask("name", "user");
        assertNotNull(task);
        assertEquals("name", task.getName());
        assertEquals("user", task.getUser());
        assertTrue(task.getTimestamp() > 0);
        assertNotNull(task.getId());
        assertTrue(task.getId().startsWith("name:"));
        assertEquals(BackfillStatus.SUBMITTED.name(), task.getStatus());
        task = backfillDao.getTask(task.getId());
        assertNotNull(task);
        assertEquals("name", task.getName());
        assertEquals("user", task.getUser());
        assertTrue(task.getTimestamp() > 0);
        assertNotNull(task.getId());
        assertTrue(task.getId().startsWith("name:"));
        assertEquals(BackfillStatus.SUBMITTED.name(), task.getStatus());
        List<? extends BackfillTask> tasks = backfillDao.getTasks("name",
                DateTime.now(DateTimeZone.UTC).getMillis() - 1000L);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
    }
}
