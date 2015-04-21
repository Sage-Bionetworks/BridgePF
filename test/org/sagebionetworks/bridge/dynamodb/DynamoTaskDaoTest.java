package org.sagebionetworks.bridge.dynamodb;

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
        hashKey.setStudyHealthCodeKey(STUDY_IDENTIFIER, HEALTH_CODE);
        
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
    private void addQuery(DynamoDBMapper mapper, DynamoDBQueryExpression<DynamoTask> query) {
        List<DynamoTask> results = Lists.newArrayList();
        
        final PaginatedQueryList<DynamoTask> queryResults = (PaginatedQueryList<DynamoTask>)mock(PaginatedQueryList.class);
        when(queryResults.iterator()).thenReturn(results.iterator());
        when(queryResults.toArray()).thenReturn(results.toArray());
        
        when(mapper.query(any(Class.class), any(DynamoDBQueryExpression.class))).thenReturn(queryResults);
    }
    
    private List<SchedulePlan> getSchedulePlans() {
        List<SchedulePlan> plans = Lists.newArrayListWithCapacity(3);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        plan.setStrategy(getStrategy("1", "P1D", "task1"));
        plan.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("CCC");
        plan.setStrategy(getStrategy("2", "P2D", "task2"));
        plan.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("DDD");
        plan.setStrategy(getStrategy("3", "P3D", "task3"));
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
    
    // NOTE: In these tests, the tasks are ultimately being created by the scheduler.
    
    @Test
    public void can() throws Exception {
        DynamoDBQueryExpression<DynamoTask> query = createQuery(NOW.plusDays(2), NOW.plusDays(3));
        addQuery(mapper, query);
        
        List<Task> tasks = taskDao.getTasks(STUDY_IDENTIFIER, user, Period.parse("P1D"), Period.parse("P3D"));
        System.out.println(tasks.size());
        for (Task task : tasks) {
            System.out.println(task);
        }
        //assertEquals(0, tasks.size());
    }
    
}
