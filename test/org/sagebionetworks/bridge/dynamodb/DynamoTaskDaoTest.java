package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskStatus;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

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
        schedule.addActivity(new Activity("Label 1", "task:task1"));
        
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
        
        UserConsentDao userConsentDao = mock(UserConsentDao.class);
        when(userConsentDao.getUserConsent(healthCode, TestConstants.TEST_STUDY)).thenReturn(consent);
        
        taskDao.setUserConsentDao(userConsentDao);
        
        user = new User();
        user.setHealthCode(healthCode);
        user.setStudyKey(TestConstants.TEST_STUDY_IDENTIFIER);
    }
    
    @After
    public void after() {
        schedulePlanService.deleteSchedulePlan(TestConstants.TEST_STUDY, plan.getGuid());
    }

    @Test
    public void createUpdateDeleteTasks() throws Exception {
        DateTime startsOn = DateTime.now().minus(Period.parse("P1D"));
        DateTime endsOn = DateTime.now().plus(Period.parse("P4D"));
        
        List<Task> tasks = taskDao.getTasks(user, startsOn, endsOn);
        int collectionSize = tasks.size();
        
        // Should not increase the number of tasks
        tasks = taskDao.getTasks(user, startsOn, endsOn);
        assertEquals(collectionSize, tasks.size());
        
        // Delete most information in tasks and delete one by finishing it
        cleanTasks(tasks);
        Task task = tasks.get(1);
        task.setFinishedOn(new DateTime().getMillis());
        assertEquals(TaskStatus.DELETED, task.getStatus());
        taskDao.updateTasks(user.getHealthCode(), tasks);
        
        tasks = taskDao.getTasks(user, startsOn, endsOn);
        assertEquals(collectionSize-1, tasks.size());
        
        taskDao.deleteTasks(user.getHealthCode());
        
        tasks = taskDao.getTasksWithoutScheduling(user, startsOn, endsOn);
        assertEquals(0, tasks.size());
    }

    private void cleanTasks(List<Task> tasks) {
        for (Task task : tasks) {
            task.setHealthCode(null);
            task.setActivity(null);
            task.setSchedulePlanGuid(null);
        }
    }
    
}
