package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskStatus;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.SchedulePlanService;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.Lists;

public class DynamoTaskDaoMockTest {

    /*
     * This is the event against which scheduling occurs.
     */
    private static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    private static final DateTime NOW = DateTime.parse("2015-04-12T14:20:56.123-07:00");
    
    private static final String HEALTH_CODE = "AAA";

    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("mock-study");
    
    private User user;
    
    private DynamoDBMapper mapper;
    
    private SchedulePlanService schedulePlanService;
    
    private UserConsentDao userConsentDao;
    
    private DynamoTaskDao taskDao;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());
        
        user = new User();
        user.setHealthCode(HEALTH_CODE);
        user.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        
        schedulePlanService = mock(SchedulePlanService.class);
        when(schedulePlanService.getSchedulePlans(STUDY_IDENTIFIER)).thenReturn(getSchedulePlans());
        
        UserConsent consent = mock(DynamoUserConsent2.class);
        when(consent.getSignedOn()).thenReturn(ENROLLMENT.getMillis()); 
        
        userConsentDao = mock(UserConsentDao.class);
        when(userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER)).thenReturn(consent);
        
        // This is the part that will need to be expanded per test.
        mapper = mock(DynamoDBMapper.class);
        when(mapper.query(eq(DynamoTask.class), any(DynamoDBQueryExpression.class))).thenReturn(null);
        
        taskDao = new DynamoTaskDao();
        taskDao.setSchedulePlanService(schedulePlanService);
        taskDao.setUserConsentDao(userConsentDao);
        taskDao.setDdbMapper(mapper);
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    private List<SchedulePlan> getSchedulePlans() {
        List<SchedulePlan> plans = Lists.newArrayListWithCapacity(3);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        // plan.setGuid("DDD");
        plan.setGuid(BridgeUtils.generateGuid());
        plan.setStrategy(getStrategy("3", "P3D", "task3"));
        plan.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        plan.setStrategy(getStrategy("1", "P1D", "task1"));
        plan.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("CCC");
        plan.setStrategy(getStrategy("2", "P2D", "task2"));
        plan.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        plans.add(plan);

        return plans;
    }
    
    private ScheduleStrategy getStrategy(String label, String interval, String activityRef) {
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule " + label);
        schedule.setInterval(interval);
        schedule.setDelay("P1D");
        schedule.addTimes("13:00");
        schedule.setExpires("PT10H");
        schedule.addActivity(new Activity("Activity " + label, "task:"+activityRef));
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        return strategy;
    }
    
    @SuppressWarnings("unchecked")
    private void mockQuery(DateTime until, final DynamoTask... tasks) {
        List<DynamoTask> results = Lists.newArrayList();
        if (tasks != null) {
            for (final DynamoTask task : tasks) {
                results.add(task);
            }
            when(mapper.load(any())).thenAnswer(new Answer<DynamoTask>() {
                @Override public DynamoTask answer(InvocationOnMock invocation) throws Throwable {
                    DynamoTask thisTask = (DynamoTask)invocation.getArguments()[0];
                    for (Task task : tasks) {
                        if (thisTask.getGuid().equals(task.getGuid()) && 
                            thisTask.getHealthCode().equals(task.getHealthCode())) {
                            return thisTask;
                        }
                    }
                    return null;
                }
                
            });
        }
        final PaginatedQueryList<DynamoTask> queryResults = (PaginatedQueryList<DynamoTask>)mock(PaginatedQueryList.class);
        when(queryResults.iterator()).thenReturn(results.iterator());
        when(queryResults.toArray()).thenReturn(results.toArray());
        when(mapper.query(any(Class.class), any(DynamoDBQueryExpression.class))).thenReturn(queryResults);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testOfFirstPeriod() throws Exception {
        DateTime endsOn = NOW.plus(Period.parse("P2D"));

        mockQuery(endsOn);
        
        List<Task> tasks = taskDao.getTasks(user, endsOn);
        
        // These also show that stuff is getting sorted by label
        /* Expired tasks are not returned, so this starts on the 12th */
        assertTask("2015-04-12T13:00:00.000-07:00", "task:task1", tasks.get(0));
        assertTask("2015-04-13T13:00:00.000-07:00", "task:task1", tasks.get(1));
        assertTask("2015-04-13T13:00:00.000-07:00", "task:task2", tasks.get(2));
        assertTask("2015-04-14T13:00:00.000-07:00", "task:task1", tasks.get(3));
        assertTask("2015-04-14T13:00:00.000-07:00", "task:task3", tasks.get(4));
        
        verify(mapper).query(any(Class.class), any(DynamoDBQueryExpression.class));
        verify(mapper).batchSave(any(List.class));
        verifyNoMoreInteractions(mapper);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testOfSecondPeriodWithDifferentStartTime() throws Exception {
        DateTime endsOn = NOW.plus(Period.parse("P4D"));
        
        mockQuery(endsOn);
        
        List<Task> tasks = taskDao.getTasks(user, endsOn);

        // These also show that stuff is getting sorted by label
        assertTask("2015-04-12T13:00:00.000-07:00", "task:task1", tasks.get(0));
        assertTask("2015-04-13T13:00:00.000-07:00", "task:task1", tasks.get(1));
        assertTask("2015-04-13T13:00:00.000-07:00", "task:task2", tasks.get(2));
        assertTask("2015-04-14T13:00:00.000-07:00", "task:task1", tasks.get(3));
        assertTask("2015-04-14T13:00:00.000-07:00", "task:task3", tasks.get(4));
        assertTask("2015-04-15T13:00:00.000-07:00", "task:task1", tasks.get(5));
        assertTask("2015-04-15T13:00:00.000-07:00", "task:task2", tasks.get(6));
        assertTask("2015-04-16T13:00:00.000-07:00", "task:task1", tasks.get(7));
        
        verify(mapper).query(any(Class.class), any(DynamoDBQueryExpression.class));
        verify(mapper).batchSave(any(List.class));
        verifyNoMoreInteractions(mapper);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testIntegrationOfQueryResults() {
        DateTime endsOn = NOW.plus(Period.parse("P2D"));
        
        DynamoTask task1 = new DynamoTask();
        task1.setSchedulePlanGuid("BBB");
        task1.setActivity(new Activity("Activity 1", "task:task1"));
        task1.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task1.setExpiresOn(DateTime.parse("2015-04-12T23:00:00.000-07:00").getMillis());
        task1.setStartedOn(DateTime.parse("2015-04-12T18:30:23.334-07:00").getMillis());
        task1.setGuid(BridgeUtils.generateTaskGuid(task1));
        
        DynamoTask task2 = new DynamoTask();
        task2.setSchedulePlanGuid("DDD");
        task2.setActivity(new Activity("Activity 3", "task:task3"));
        task2.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task2.setExpiresOn(DateTime.parse("2015-04-12T23:00:00.000-07:00").getMillis());
        task2.setFinishedOn(DateTime.parse("2015-04-12T18:34:01.113-07:00").getMillis());
        task2.setGuid(BridgeUtils.generateTaskGuid(task2));
        
        mockQuery(endsOn, task1, task2);
        
        List<Task> tasks = taskDao.getTasks(user, endsOn);
        
        assertEquals(TaskStatus.STARTED, tasks.get(0).getStatus());
        assertEquals(TaskStatus.AVAILABLE, tasks.get(1).getStatus());
        assertEquals(TaskStatus.SCHEDULED, tasks.get(2).getStatus());
        assertTask("2015-04-11T13:00:00.000-07:00", "task:task1", tasks.get(0));
        assertTask("2015-04-12T13:00:00.000-07:00", "task:task1", tasks.get(1));
        assertTask("2015-04-13T13:00:00.000-07:00", "task:task1", tasks.get(2));
        assertTask("2015-04-13T13:00:00.000-07:00", "task:task2", tasks.get(3));
        assertTask("2015-04-14T13:00:00.000-07:00", "task:task1", tasks.get(4));
        assertTask("2015-04-14T13:00:00.000-07:00", "task:task3", tasks.get(5));
        
        
        verify(mapper).query(any(Class.class), any(DynamoDBQueryExpression.class));
        verify(mapper).batchSave(any(List.class));
        verifyNoMoreInteractions(mapper);
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void canDeleteTasks() {
        DynamoTask task1 = new DynamoTask();
        task1.setSchedulePlanGuid("BBB");
        task1.setActivity(new Activity("Activity 1", "task:task1"));
        task1.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task1.setExpiresOn(DateTime.parse("2015-04-12T23:00:00.000-07:00").getMillis());
        task1.setStartedOn(DateTime.parse("2015-04-12T18:30:23.334-07:00").getMillis());
        task1.setGuid(BridgeUtils.generateTaskGuid(task1));
        
        DynamoTask task2 = new DynamoTask();
        task2.setSchedulePlanGuid("DDD");
        task2.setActivity(new Activity("Activity 3", "task:task3"));
        task2.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task2.setExpiresOn(DateTime.parse("2015-04-12T23:00:00.000-07:00").getMillis());
        task2.setFinishedOn(DateTime.parse("2015-04-12T18:34:01.113-07:00").getMillis());
        task2.setGuid(BridgeUtils.generateTaskGuid(task2));
        
        mockQuery(NOW.plusDays(2), task1, task2);
        
        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        taskDao.deleteTasks("AAA");
        
        verify(mapper).query(any(Class.class), any(DynamoDBQueryExpression.class));
        verify(mapper).batchDelete(argument.capture());
        verifyNoMoreInteractions(mapper);
        
        // Both tasks were passed in to be deleted.
        assertEquals(2, argument.getValue().size());
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void canUpdateTasks() {
        DynamoTask task1 = new DynamoTask();
        task1.setHealthCode(HEALTH_CODE);
        task1.setSchedulePlanGuid("BBB");
        task1.setActivity(new Activity("Activity 1", "task:task1"));
        task1.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task1.setGuid(BridgeUtils.generateTaskGuid(task1));
        DynamoTask task2 = new DynamoTask();
        
        task2.setSchedulePlanGuid("DDD");
        task2.setHealthCode(HEALTH_CODE);
        task2.setActivity(new Activity("Activity 3", "task:task3"));
        task2.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task2.setStartedOn(DateTime.parse("2015-04-12T18:30:23.334-07:00").getMillis());
        task2.setGuid(BridgeUtils.generateTaskGuid(task2));
        
        mockQuery(NOW.plusDays(2), task1, task2);
        
        Task task3 = new DynamoTask();
        task3.setSchedulePlanGuid("BBB");
        task3.setHealthCode(HEALTH_CODE);
        task3.setActivity(new Activity("Activity 1", "task:task1"));
        task3.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task3.setStartedOn(DateTime.parse("2015-04-13T14:23:12.000-07:00").getMillis());
        task3.setGuid(BridgeUtils.generateTaskGuid(task3));

        Task task4 = new DynamoTask();
        task4.setSchedulePlanGuid("DDD");
        task4.setHealthCode(HEALTH_CODE);
        task4.setActivity(new Activity("Activity 3", "task:task3"));
        task4.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task4.setStartedOn(DateTime.parse("2015-04-13T18:05:23.000-07:00").getMillis());
        task4.setFinishedOn(DateTime.parse("2015-04-13T18:20:23.000-07:00").getMillis());
        task4.setGuid(BridgeUtils.generateTaskGuid(task4));
        
        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        List<Task> tasks = Lists.newArrayList(task3, task4);
        taskDao.updateTasks(HEALTH_CODE, tasks);

        // So yeah, those tasks have been updated. Capture them and verify that they were updated
        verify(mapper, times(2)).load(any());
        verify(mapper).batchSave(argument.capture());
        verifyNoMoreInteractions(mapper);
        
        List<DynamoTask> list = new ArrayList<>(argument.getValue());
        DynamoTask savedTask1 = list.get(0);
        DynamoTask savedTask2 = list.get(1);
        assertEquals(TaskStatus.STARTED, savedTask1.getStatus());
        assertEquals(TaskStatus.FINISHED, savedTask2.getStatus());
    }
    
    private void assertTask(String dateString, String ref, Task task) {
        DateTime date = DateTime.parse(dateString);
        assertEquals(date.toString(), new DateTime(task.getScheduledOn()).toString());
        assertEquals(ref, task.getActivity().getRef());
    }
    
}
