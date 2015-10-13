package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskStatus;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoTaskDaoTest {
    
    @Resource
    DynamoTaskDao taskDao;

    @Resource
    SchedulePlanService schedulePlanService;
    
    private SchedulePlan plan;
    
    private User user;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoTask.class);
        DynamoTestUtil.clearTable(DynamoTask.class);
        
        Schedule schedule = new Schedule();
        schedule.setLabel("This is a schedule");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("PT6H");
        schedule.addTimes("10:00", "14:00");
        schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        plan = new DynamoSchedulePlan();
        plan.setLabel("And this is a schedule plan");
        plan.setStudyKey(TestConstants.TEST_STUDY_IDENTIFIER);
        plan.setStrategy(strategy);
        
        plan = schedulePlanService.createSchedulePlan(plan);

        String healthCode = BridgeUtils.generateGuid();
        
        // Mock user consent, we don't care about that, we're just getting an enrollment date from that.
        UserConsent consent = mock(DynamoUserConsent2.class);
        when(consent.getSignedOn()).thenReturn(new DateTime().minusDays(2).getMillis()); 
        
        user = new User();
        user.setHealthCode(healthCode);
        user.setStudyKey(TestConstants.TEST_STUDY_IDENTIFIER);
    }
    
    @After
    public void after() {
        schedulePlanService.deleteSchedulePlan(TestConstants.TEST_STUDY, plan.getGuid());
        taskDao.deleteTasks(user.getHealthCode());
    }

    @Test
    public void createUpdateDeleteTasks() throws Exception {
        DateTime endsOn = DateTime.now().plus(Period.parse("P4D"));
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn)
            .withHealthCode(user.getHealthCode())
            .withEvents(events).build();
        
        List<Task> tasksToSchedule = TestUtils.runSchedulerForTasks(user, context);
        taskDao.saveTasks(tasksToSchedule);
        
        List<Task> tasks = taskDao.getTasks(context);
        int collectionSize = tasks.size();
        assertFalse("tasks were created", tasks.isEmpty());
        
        // Should not increase the number of tasks
        tasks = taskDao.getTasks(context);
        assertEquals("tasks did not grow afer repeated getTask()", collectionSize, tasks.size());

        // Have tasks gotten injected time zone? We have to do this during construction using the time zone
        // sent with this call/request.
        assertEquals(DateTimeZone.UTC, ((DynamoTask)tasks.get(0)).getTimeZone());
        
        // Delete most information in tasks and delete one by finishing it
        cleanTasks(tasks);
        Task task = tasks.get(1);
        task.setFinishedOn(context.getNow().getMillis());
        // This logic is now in the service, but essential for the task to be "deleted"
        task.setHidesOn(context.getNow().getMillis());
        assertEquals("task deleted", TaskStatus.DELETED, task.getStatus());
        taskDao.updateTasks(user.getHealthCode(), Lists.newArrayList(task));
        
        tasks = taskDao.getTasks(context);
        assertEquals("deleted task not returned from server", collectionSize-1, tasks.size());
        taskDao.deleteTasks(user.getHealthCode());
        
        tasks = taskDao.getTasks(context);
        assertEquals("all tasks deleted", 0, tasks.size());
    }

    private void cleanTasks(List<Task> tasks) {
        for (Task task : tasks) {
            //task.setActivity(null);
            task.setStartedOn(null);
            task.setFinishedOn(null);
        }
    }
    
}
