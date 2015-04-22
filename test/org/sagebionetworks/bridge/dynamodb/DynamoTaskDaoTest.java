package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;

public class DynamoTaskDaoTest {

    /*
     * This is the event against which scheduling occurs. According to the schedules, calculated by hand, 
     * we expect the following schedules to be returned:
     * 
     * NOW: 4/12/15
     * 
     * task1
     *      4/11/15 13:00-23:00
     *      4/12/15 13:00-23:00
     *      4/13/15 13:00-23:00
     *      4/14/15 13:00-23:00
     *      4/15/15 13:00-23:00
     * 
     * task2
     *      4/11/15 13:00-23:00
     *      4/13/15 13:00-23:00
     *      4/15/15 13:00-23:00
     *      4/17/15 13:00-23:00
     *      4/19/15 13:00-23:00
     * 
     * task3
     *      4/11/15 13:00-23:00
     *      4/14/15 13:00-23:00
     *      4/17/15 13:00-23:00
     *      4/20/15 13:00-23:00
     *      4/23/15 13:00-23:00
     */
    private static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    private static final DateTime NOW = DateTime.parse("2015-04-12T14:20:56.123-07:00");
    
    private static final String HEALTH_CODE = "AAA";

    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("mock-study");;
    
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
    
    private DynamoDBQueryExpression<DynamoTask> createQuery(DateTime from, DateTime until) {
        DynamoTask hashKey = new DynamoTask();
        hashKey.setHealthCode(HEALTH_CODE);
        
        Condition rangeKeyCondition = new Condition()
            .withComparisonOperator(ComparisonOperator.BETWEEN.toString())
            .withAttributeValueList(new AttributeValue().withS(from.toString()), 
                                    new AttributeValue().withS(until.toString()));
    
        DynamoDBQueryExpression<DynamoTask> query = new DynamoDBQueryExpression<DynamoTask>()
            .withHashKeyValues(hashKey)
            .withRangeKeyCondition("scheduledOn", rangeKeyCondition);
        return query;
    }
    
    @SuppressWarnings("unchecked")
    private void addQuery(DynamoDBMapper mapper, DynamoDBQueryExpression<DynamoTask> query, DynamoTask... tasks) {
        List<DynamoTask> results = Lists.newArrayList();
        if (tasks != null) {
            for (DynamoTask task : tasks) {
                results.add(task);
            }
        }
        final PaginatedQueryList<DynamoTask> queryResults = (PaginatedQueryList<DynamoTask>)mock(PaginatedQueryList.class);
        when(queryResults.iterator()).thenReturn(results.iterator());
        when(queryResults.toArray()).thenReturn(results.toArray());
        
        when(mapper.query(any(Class.class), any(DynamoDBQueryExpression.class))).thenReturn(queryResults);
    }
    
    private List<SchedulePlan> getSchedulePlans() {
        List<SchedulePlan> plans = Lists.newArrayListWithCapacity(3);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("DDD");
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
    
    @Test
    public void testOfFirstPeriod() throws Exception {
        Period before = Period.parse("P2D");
        Period after = Period.parse("P2D");
        // This query isn't tied to anything... will create similar tests that use DDB
        DynamoDBQueryExpression<DynamoTask> query = createQuery(NOW.plus(before), NOW.plus(after));
        addQuery(mapper, query);
        
        List<Task> tasks = taskDao.getTasks(user, before, after);
        
        // These also show that stuff is getting sorted by label
        assertTask(DateTime.parse("2015-04-11T13:00:00.000-07:00"), "task:task1", tasks.get(0));
        assertTask(DateTime.parse("2015-04-11T13:00:00.000-07:00"), "task:task2", tasks.get(1));
        assertTask(DateTime.parse("2015-04-11T13:00:00.000-07:00"), "task:task3", tasks.get(2));
        assertTask(DateTime.parse("2015-04-12T13:00:00.000-07:00"), "task:task1", tasks.get(3));
        assertTask(DateTime.parse("2015-04-13T13:00:00.000-07:00"), "task:task1", tasks.get(4));
        assertTask(DateTime.parse("2015-04-13T13:00:00.000-07:00"), "task:task2", tasks.get(5));
        assertTask(DateTime.parse("2015-04-14T13:00:00.000-07:00"), "task:task1", tasks.get(6));
        assertTask(DateTime.parse("2015-04-14T13:00:00.000-07:00"), "task:task3", tasks.get(7));
    }
    
    @Test
    public void testOfSecondPeriodWithDifferentStartTime() throws Exception {
        Period before = Period.parse("P1D");
        Period after = Period.parse("P4D");
        // This query isn't tied to anything... will create similar tests that use DDB
        DynamoDBQueryExpression<DynamoTask> query = createQuery(NOW.plus(before), NOW.plus(after));
        addQuery(mapper, query);
        
        List<Task> tasks = taskDao.getTasks(user, before, after);

        // These also show that stuff is getting sorted by label
        assertTask(DateTime.parse("2015-04-12T13:00:00.000-07:00"), "task:task1", tasks.get(0));
        assertTask(DateTime.parse("2015-04-13T13:00:00.000-07:00"), "task:task1", tasks.get(1));
        assertTask(DateTime.parse("2015-04-13T13:00:00.000-07:00"), "task:task2", tasks.get(2));
        assertTask(DateTime.parse("2015-04-14T13:00:00.000-07:00"), "task:task1", tasks.get(3));
        assertTask(DateTime.parse("2015-04-14T13:00:00.000-07:00"), "task:task3", tasks.get(4));
        assertTask(DateTime.parse("2015-04-15T13:00:00.000-07:00"), "task:task1", tasks.get(5));
        assertTask(DateTime.parse("2015-04-15T13:00:00.000-07:00"), "task:task2", tasks.get(6));
        assertTask(DateTime.parse("2015-04-16T13:00:00.000-07:00"), "task:task1", tasks.get(7));
    }
    
    @Test
    public void testIntegrationOfQueryResults() {
        Period before = Period.parse("P2D");
        Period after = Period.parse("P2D");
        
        DynamoTask task1 = new DynamoTask();
        task1.setSchedulePlanGuid("BBB");
        task1.setActivity(new Activity("Activity 1", "task:task1"));
        task1.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00"));
        task1.setExpiresOn(DateTime.parse("2015-04-12T23:00:00.000-07:00"));
        task1.setStartedOn(DateTime.parse("2015-04-12T18:30:23.334-07:00"));
        
        DynamoTask task2 = new DynamoTask();
        task2.setSchedulePlanGuid("DDD");
        task2.setActivity(new Activity("Activity 3", "task:task3"));
        task2.setScheduledOn(DateTime.parse("2015-04-11T13:00:00.000-07:00"));
        task2.setExpiresOn(DateTime.parse("2015-04-12T23:00:00.000-07:00"));
        task2.setFinishedOn(DateTime.parse("2015-04-12T18:34:01.113-07:00"));
        
        DynamoDBQueryExpression<DynamoTask> query = createQuery(NOW.plus(before), NOW.plus(after));
        addQuery(mapper, query, task1, task2);
        
        List<Task> tasks = taskDao.getTasks(user, before, after);
        
        for (Task task : tasks) {
            System.out.println(task.getStatus() + ": " + task.getNaturalKey());
        }

        assertEquals(TaskStatus.STARTED, tasks.get(0).getStatus());
        assertEquals(TaskStatus.EXPIRED, tasks.get(1).getStatus());
        assertEquals(TaskStatus.FINISHED, tasks.get(2).getStatus());
        assertTask(DateTime.parse("2015-04-11T13:00:00.000-07:00"), "task:task1", tasks.get(0));
        assertTask(DateTime.parse("2015-04-11T13:00:00.000-07:00"), "task:task2", tasks.get(1));
        assertTask(DateTime.parse("2015-04-11T13:00:00.000-07:00"), "task:task3", tasks.get(2));
        
    }
    
    private void assertTask(DateTime date, String ref, Task task) {
        assertEquals(date.toString(), new DateTime(task.getScheduledOn()).toString());
        assertEquals(ref, task.getActivity().getRef());
    }
    
}
