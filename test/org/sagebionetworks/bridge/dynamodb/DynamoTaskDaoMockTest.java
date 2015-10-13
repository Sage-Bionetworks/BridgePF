package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DynamoTaskDaoMockTest {

    private static final DateTime NOW = DateTime.parse("2015-04-12T14:20:56.123-07:00");

    private static final String HEALTH_CODE = "AAA";

    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("mock-study");

    private static final DateTimeZone PACIFIC_TIME_ZONE = DateTimeZone.forOffsetHours(-7);

    private User user;

    private DynamoDBMapper mapper;

    private DynamoTaskDao taskDao;
    
    private DynamoTask testTask;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());

        user = new User();
        user.setHealthCode(HEALTH_CODE);
        user.setStudyKey(STUDY_IDENTIFIER.getIdentifier());

        testTask = new DynamoTask();
        
        // This is the part that will need to be expanded per test.
        mapper = mock(DynamoDBMapper.class);
        when(mapper.query((Class<DynamoTask>) any(Class.class),
            (DynamoDBQueryExpression<DynamoTask>) any(DynamoDBQueryExpression.class)))
            .thenReturn(null);
        when(mapper.load(any(DynamoTask.class))).thenReturn(testTask);
        taskDao = new DynamoTaskDao();
        taskDao.setDdbMapper(mapper);
    }

    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @SuppressWarnings("unchecked")
    private void mockQuery(final List<Task> tasks) {
        when(mapper.load(any())).thenAnswer(invocation -> {
            DynamoTask thisTask = invocation.getArgumentAt(0, DynamoTask.class);
            for (Task task : tasks) {
                if (thisTask.getGuid().equals(task.getGuid()) && thisTask.getHealthCode().equals(task.getHealthCode())) {
                    return thisTask;
                }
            }
            return null;
        });
        final PaginatedQueryList<DynamoTask> queryResults = (PaginatedQueryList<DynamoTask>) mock(PaginatedQueryList.class);
        when(queryResults.iterator()).thenReturn(((List<DynamoTask>)(List<?>)tasks).iterator());
        when(queryResults.toArray()).thenReturn(tasks.toArray());
        when(mapper.query((Class<DynamoTask>) any(Class.class),
            (DynamoDBQueryExpression<DynamoTask>) any(DynamoDBQueryExpression.class)))
            .thenReturn(queryResults);
    }
    
    @Test
    public void getTask() throws Exception {
        Task task = taskDao.getTask("AAA", "BBB");
        assertEquals(testTask, task);
        
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getTaskThrowsException() throws Exception {
        when(mapper.load(any(DynamoTask.class))).thenReturn(null);
        
        taskDao.getTask("AAA", "BBB");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOfFirstPeriod() throws Exception {
        DateTime endsOn = NOW.plus(Period.parse("P2D"));
        Map<String, DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(PACIFIC_TIME_ZONE)
            .withEndsOn(endsOn)
            .withHealthCode(HEALTH_CODE)
            .withEvents(events).build();

        List<Task> tasks = TestUtils.runSchedulerForTasks(user, context);
        mockQuery(tasks);
        List<Task> tasks2 = taskDao.getTasks(context);

        // Tasks are sorted first by date, then by label ("Activity1", "Activity2" & "Activity3")
        // Expired tasks are not returned, so this starts on the 12th
        assertTask(tasks2.get(0), TestConstants.ACTIVITY_2_REF, "2015-04-12T13:00:00-07:00");
        assertTask(tasks2.get(1), TestConstants.ACTIVITY_2_REF, "2015-04-13T13:00:00-07:00");
        assertTask(tasks2.get(2), TestConstants.ACTIVITY_3_REF, "2015-04-13T13:00:00-07:00");
        assertTask(tasks2.get(3), TestConstants.ACTIVITY_1_REF, "2015-04-14T13:00:00-07:00");
        assertTask(tasks2.get(4), TestConstants.ACTIVITY_2_REF, "2015-04-14T13:00:00-07:00");

        verify(mapper).query((Class<DynamoTask>) any(Class.class),
                        (DynamoDBQueryExpression<DynamoTask>) any(DynamoDBQueryExpression.class));
        verifyNoMoreInteractions(mapper);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void taskSchedulerFiltersTasks() throws Exception {
        DateTime endsOn = NOW.plus(Period.parse("P4D"));
        Map<String, DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.fromUserAgentCache("App/5"))
            .withTimeZone(PACIFIC_TIME_ZONE)
            .withEndsOn(endsOn)
            .withHealthCode(HEALTH_CODE)
            .withEvents(events).build();

        List<Task> tasks = TestUtils.runSchedulerForTasks(user, context);
        mockQuery(tasks);
        List<Task> tasks2 = taskDao.getTasks(context);

        // The test schedules have these appVersions applied to them
        // SchedulePlan DDD/Activity 1: version 2-5
        // SchedulePlan BBB/Activity 2: version 9+
        // SchedulePlan CCC/Activity 3: version 5-8
        // Activity_1 and Activity_3 will match v5, Activity_2 will not. These results are 
        // just like the next test of 4 days, but without the Activity_2 tasks
        // Tasks are sorted first by date, then by label ("Activity1", "Activity2" & "Activity3")
        assertEquals(3, tasks2.size());
        assertTask(tasks2.get(0), TestConstants.ACTIVITY_3_REF, "2015-04-13T13:00:00-07:00");
        assertTask(tasks2.get(1), TestConstants.ACTIVITY_1_REF, "2015-04-14T13:00:00-07:00");
        assertTask(tasks2.get(2), TestConstants.ACTIVITY_3_REF, "2015-04-15T13:00:00-07:00");

        verify(mapper).query((Class<DynamoTask>) any(Class.class),
                        (DynamoDBQueryExpression<DynamoTask>) any(DynamoDBQueryExpression.class));
        verifyNoMoreInteractions(mapper);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testOfSecondPeriodWithDifferentStartTime() throws Exception {
        DateTime endsOn = NOW.plus(Period.parse("P4D"));
        Map<String, DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);

        ScheduleContext context = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(PACIFIC_TIME_ZONE)
            .withEndsOn(endsOn)
            .withHealthCode(HEALTH_CODE)
            .withEvents(events).build();

        List<Task> tasks = TestUtils.runSchedulerForTasks(user, context);
        mockQuery(tasks);

        List<Task> tasks2 = taskDao.getTasks(context);

        // Tasks are sorted first by date, then by label ("Activity1", "Activity2" & "Activity3")
        assertTask(tasks2.get(0), TestConstants.ACTIVITY_2_REF, "2015-04-12T13:00:00.000-07:00");
        assertTask(tasks2.get(1), TestConstants.ACTIVITY_2_REF, "2015-04-13T13:00:00.000-07:00");
        assertTask(tasks2.get(2), TestConstants.ACTIVITY_3_REF, "2015-04-13T13:00:00.000-07:00");
        assertTask(tasks2.get(3), TestConstants.ACTIVITY_1_REF, "2015-04-14T13:00:00.000-07:00");
        assertTask(tasks2.get(4), TestConstants.ACTIVITY_2_REF, "2015-04-14T13:00:00.000-07:00");
        assertTask(tasks2.get(5), TestConstants.ACTIVITY_2_REF, "2015-04-15T13:00:00.000-07:00");
        assertTask(tasks2.get(6), TestConstants.ACTIVITY_3_REF, "2015-04-15T13:00:00.000-07:00");
        assertTask(tasks2.get(7), TestConstants.ACTIVITY_2_REF, "2015-04-16T13:00:00.000-07:00");

        verify(mapper).query((Class<DynamoTask>) any(Class.class),
                        (DynamoDBQueryExpression<DynamoTask>) any(DynamoDBQueryExpression.class));
        verifyNoMoreInteractions(mapper);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void canDeleteTasks() {
        DynamoTask task1 = new DynamoTask();
        task1.setActivity(TestConstants.TEST_3_ACTIVITY);
        task1.setLocalScheduledOn(LocalDateTime.parse("2015-04-11T13:00:00"));
        task1.setLocalExpiresOn(LocalDateTime.parse("2015-04-12T23:00:00"));
        task1.setStartedOn(DateTime.parse("2015-04-12T18:30:23").getMillis());
        task1.setGuid(BridgeUtils.generateGuid());

        DynamoTask task2 = new DynamoTask();
        task2.setActivity(TestConstants.TEST_3_ACTIVITY);
        task2.setLocalScheduledOn(LocalDateTime.parse("2015-04-11T13:00:00"));
        task2.setLocalExpiresOn(LocalDateTime.parse("2015-04-12T23:00:00"));
        task2.setFinishedOn(DateTime.parse("2015-04-12T18:34:01").getMillis());
        task2.setGuid(BridgeUtils.generateGuid());

        mockQuery(Lists.newArrayList(task1, task2));

        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        taskDao.deleteTasks("AAA");

        verify(mapper).query((Class<DynamoTask>) any(Class.class),
                        (DynamoDBQueryExpression<DynamoTask>) any(DynamoDBQueryExpression.class));
        verify(mapper).batchDelete(argument.capture());
        verifyNoMoreInteractions(mapper);

        // Both tasks were passed in to be deleted.
        assertEquals(2, argument.getValue().size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void canUpdateTasks() {
        DynamoTask task1 = new DynamoTask();
        task1.setHealthCode(HEALTH_CODE);
        task1.setActivity(TestConstants.TEST_3_ACTIVITY);
        task1.setLocalScheduledOn(LocalDateTime.parse("2015-04-11T13:00:00"));
        task1.setGuid(BridgeUtils.generateGuid());

        DynamoTask task2 = new DynamoTask();
        task2.setHealthCode(HEALTH_CODE);
        task2.setActivity(TestConstants.TEST_3_ACTIVITY);
        task2.setLocalScheduledOn(LocalDateTime.parse("2015-04-11T13:00:00"));
        task2.setStartedOn(DateTime.parse("2015-04-13T18:05:23.000-07:00").getMillis());
        task2.setFinishedOn(DateTime.parse("2015-04-13T18:20:23.000-07:00").getMillis());
        task2.setGuid(BridgeUtils.generateGuid());
        
        List<Task> tasks = Lists.newArrayList(task1, task2);
        taskDao.updateTasks(HEALTH_CODE, tasks);

        // These tasks have been updated.
        verify(mapper).batchSave(any(List.class));
        verifyNoMoreInteractions(mapper);
    }
    
    private void assertTask(Task task, String ref, String dateString) {
        DateTime date = DateTime.parse(dateString);
        assertTrue(date.isEqual(task.getScheduledOn()));
        if (task.getActivity().getActivityType() == ActivityType.TASK) {
            assertEquals(ref, task.getActivity().getTask().getIdentifier());            
        } else {
            assertEquals(ref, task.getActivity().getSurvey().getHref());
        }
    }

}
