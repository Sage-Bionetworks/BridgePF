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
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskStatus;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.Lists;

public class DynamoTaskDaoMockTest {

    private static final DateTime NOW = DateTime.parse("2015-04-12T14:20:56.123-07:00");
    
    private static final String HEALTH_CODE = "AAA";

    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("mock-study");
    
    private User user;
    
    private DynamoDBMapper mapper;
    
    private DynamoTaskDao taskDao;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());
        
        user = new User();
        user.setHealthCode(HEALTH_CODE);
        user.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        
        // This is the part that will need to be expanded per test.
        mapper = mock(DynamoDBMapper.class);
        when(mapper.query(eq(DynamoTask.class), any(DynamoDBQueryExpression.class))).thenReturn(null);
        
        taskDao = new DynamoTaskDao();
        taskDao.setDdbMapper(mapper);
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @SuppressWarnings("unchecked")
    private void mockQuery(DateTime until, final List<Task> tasks) {
        List<DynamoTask> results = Lists.newArrayList();
        if (tasks != null) {
            for (final Task task : tasks) {
                results.add((DynamoTask)task);
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
        
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, endsOn);
        mockQuery(endsOn, tasks);
        
        List<Task> tasks2 = taskDao.getTasks(HEALTH_CODE, endsOn);
        
        // These also show that stuff is getting sorted by label
        /* Expired tasks are not returned, so this starts on the 12th */
        assertTask("2015-04-12T13:00:00.000-07:00", TestConstants.ACTIVITY_2_REF, tasks2.get(0));
        assertTask("2015-04-13T13:00:00.000-07:00", TestConstants.ACTIVITY_2_REF, tasks2.get(1));
        assertTask("tapTest", TestConstants.ACTIVITY_3_REF, tasks2.get(2));
        assertTask("2015-04-14T13:00:00.000-07:00", TestConstants.ACTIVITY_2_REF, tasks2.get(3));
        assertTask("2015-04-14T13:00:00.000-07:00", TestConstants.ACTIVITY_1_REF, tasks2.get(4));
        
        verify(mapper).query(any(Class.class), any(DynamoDBQueryExpression.class));
        verifyNoMoreInteractions(mapper);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testOfSecondPeriodWithDifferentStartTime() throws Exception {
        DateTime endsOn = NOW.plus(Period.parse("P4D"));
        
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, endsOn);
        mockQuery(endsOn, tasks);
        
        List<Task> tasks2 = taskDao.getTasks(HEALTH_CODE, endsOn);

        // These also show that stuff is getting sorted by label
        assertTask("2015-04-12T13:00:00.000-07:00", TestConstants.ACTIVITY_2_REF, tasks2.get(0));
        assertTask("2015-04-13T13:00:00.000-07:00", TestConstants.ACTIVITY_2_REF, tasks2.get(1));
        assertTask("tapTest", TestConstants.ACTIVITY_3_REF, tasks2.get(2));
        assertTask("2015-04-14T13:00:00.000-07:00", TestConstants.ACTIVITY_2_REF, tasks2.get(3));
        assertTask("2015-04-14T13:00:00.000-07:00", TestConstants.ACTIVITY_1_REF, tasks2.get(4));
        assertTask("2015-04-15T13:00:00.000-07:00", TestConstants.ACTIVITY_2_REF, tasks2.get(5));
        assertTask("tapTest", TestConstants.ACTIVITY_3_REF, tasks2.get(6));
        assertTask("2015-04-16T13:00:00.000-07:00", TestConstants.ACTIVITY_2_REF, tasks2.get(7));
        
        verify(mapper).query(any(Class.class), any(DynamoDBQueryExpression.class));
        verifyNoMoreInteractions(mapper);
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void canDeleteTasks() {
        Task task1 = new DynamoTask();
        task1.setSchedulePlanGuid("BBB");
        task1.setActivity(TestConstants.TEST_ACTIVITY);
        task1.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task1.setExpiresOn(DateTime.parse("2015-04-12T23:00:00.000-07:00").getMillis());
        task1.setStartedOn(DateTime.parse("2015-04-12T18:30:23.334-07:00").getMillis());
        task1.setGuid(BridgeUtils.generateGuid());
        
        Task task2 = new DynamoTask();
        task2.setSchedulePlanGuid("DDD");
        task2.setActivity(TestConstants.TEST_ACTIVITY);
        task2.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task2.setExpiresOn(DateTime.parse("2015-04-12T23:00:00.000-07:00").getMillis());
        task2.setFinishedOn(DateTime.parse("2015-04-12T18:34:01.113-07:00").getMillis());
        task2.setGuid(BridgeUtils.generateGuid());
        
        mockQuery(NOW.plusDays(2), Lists.newArrayList(task1, task2));
        
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
        String guid1 = BridgeUtils.generateGuid();
        String guid2 = BridgeUtils.generateGuid();
        
        Task task1 = new DynamoTask();
        task1.setHealthCode(HEALTH_CODE);
        task1.setSchedulePlanGuid("BBB");
        task1.setActivity(TestConstants.TEST_ACTIVITY);
        task1.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task1.setGuid(guid1);
        
        Task task2 = new DynamoTask();
        task2.setSchedulePlanGuid("DDD");
        task2.setHealthCode(HEALTH_CODE);
        task2.setActivity(TestConstants.TEST_ACTIVITY);
        task2.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task2.setStartedOn(DateTime.parse("2015-04-12T18:30:23.334-07:00").getMillis());
        task2.setGuid(guid2);
        
        mockQuery(NOW.plusDays(2), Lists.newArrayList(task1, task2));
        
        Task task3 = new DynamoTask();
        task3.setSchedulePlanGuid("BBB");
        task3.setHealthCode(HEALTH_CODE);
        task3.setActivity(TestConstants.TEST_ACTIVITY);
        task3.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task3.setStartedOn(DateTime.parse("2015-04-13T14:23:12.000-07:00").getMillis());
        task3.setGuid(guid1);

        Task task4 = new DynamoTask();
        task4.setSchedulePlanGuid("DDD");
        task4.setHealthCode(HEALTH_CODE);
        task4.setActivity(TestConstants.TEST_ACTIVITY);
        task4.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00").getMillis());
        task4.setStartedOn(DateTime.parse("2015-04-13T18:05:23.000-07:00").getMillis());
        task4.setFinishedOn(DateTime.parse("2015-04-13T18:20:23.000-07:00").getMillis());
        task4.setGuid(guid2);
        
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
    
    @SuppressWarnings("deprecation")
    private void assertTask(String dateString, String ref, Task task) {
        if ("tapTest".equals(dateString)) {
            assertEquals(ref, task.getActivity().getRef());
            return;
        }
        DateTime date = DateTime.parse(dateString);
        assertEquals((long)date.getMillis(), (long)task.getScheduledOn());
        assertEquals(ref, task.getActivity().getRef());
    }
    
}
