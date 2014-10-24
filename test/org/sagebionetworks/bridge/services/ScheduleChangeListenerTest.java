package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestUtils.waitFor;

import java.util.List;
import java.util.concurrent.Callable;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedule;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduleDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlanDao;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.events.SchedulePlanCreatedEvent;
import org.sagebionetworks.bridge.events.SchedulePlanDeletedEvent;
import org.sagebionetworks.bridge.events.SchedulePlanUpdatedEvent;
import org.sagebionetworks.bridge.events.UserEnrolledEvent;
import org.sagebionetworks.bridge.events.UserUnenrolledEvent;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ScheduleChangeListenerTest {
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private ScheduleChangeListener listener;
    
    @Resource
    private DynamoScheduleDao scheduleDao;
    
    @Resource
    private DynamoSchedulePlanDao schedulePlanDao;
    
    @Resource
    private AuthenticationServiceImpl authService;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoSchedule.class, DynamoSchedulePlan.class);
        DynamoTestUtil.clearTable(DynamoSchedule.class);
        DynamoTestUtil.clearTable(DynamoSchedulePlan.class);
    }
    
    @Test
    public void addPlanThenEnrollUnenrollUser() throws Exception {
        final TestUser testUser = new TestUser("enrollme");
        final Study study = helper.getTestStudy();
        UserSession session = null;
        try {
            session = helper.createUser(testUser.getSignUp(), study, true, false);
            final User user = session.getUser();
            
            SchedulePlan plan = createSchedulePlan(user);
            schedulePlanDao.createSchedulePlan(plan);
            
            List<Schedule> schedules = scheduleDao.getSchedules(study, user);
            assertEquals("No schedules because the user hasn't joined the study", 0, schedules.size());
            
            listener.onApplicationEvent(new UserEnrolledEvent(user, study));
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return (scheduleDao.getSchedules(study, user).size() == 1);
                }
            });
            schedules = scheduleDao.getSchedules(study, user);
            assertEquals("User joined study and has a schedule", 1, schedules.size());
            
            listener.onApplicationEvent(new UserUnenrolledEvent(user, study));
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return (scheduleDao.getSchedules(study, user).size() == 0);
                }
            });
            schedules = scheduleDao.getSchedules(study, user);
            assertEquals("User left study and has no schedules", 0, schedules.size());
        } finally {
            helper.deleteUser(session);
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return (authService.getUser(study, testUser.getEmail()) == null);
                }                
            });
        }
    }
    
    @Test
    public void addUserThenCrudPlan() throws Exception {
        final TestUser testUser = new TestUser("enrollme");
        final Study study = helper.getTestStudy();
        UserSession session = null; 
        try {
            session = helper.createUser(testUser.getUsername());
            final User user = session.getUser();
            
            List<Schedule> schedules = scheduleDao.getSchedules(study, session.getUser());
            assertEquals("No schedules because there's no plan", 0, schedules.size());

            SchedulePlan plan = createSchedulePlan(session.getUser());
            listener.onApplicationEvent(new SchedulePlanCreatedEvent(plan));
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return (scheduleDao.getSchedules(study, user).size() == 1);
                }
            });
            schedules = scheduleDao.getSchedules(study, session.getUser());
            assertEquals("There is now one schedule for the user", 1, schedules.size());

            updateSchedulePlan(plan);
            listener.onApplicationEvent(new SchedulePlanUpdatedEvent(plan));
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    List<Schedule> sch = scheduleDao.getSchedules(study, user);
                    return (!sch.isEmpty() && "* * * * * *".equals(sch.get(0).getCronTrigger()));
                }
            });
            schedules = scheduleDao.getSchedules(study, session.getUser());
            assertEquals("There is still one schedule for the user", 1, schedules.size());
            assertEquals("That schedule shows an update", "* * * * * *", schedules.get(0).getCronTrigger());

            listener.onApplicationEvent(new SchedulePlanDeletedEvent(plan));
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return (scheduleDao.getSchedules(study, user).size() == 0);
                }
            });
            schedules = scheduleDao.getSchedules(study, session.getUser());
            assertEquals("Now there is no schedule after the one plan was deleted", 0, schedules.size());
            
        } finally {
            helper.deleteUser(session, getClass().getSimpleName());
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return (authService.getUser(study, testUser.getEmail()) == null);
                }                
            });
        }
    }
    
    private void updateSchedulePlan(SchedulePlan plan) {
        SimpleScheduleStrategy strategy = (SimpleScheduleStrategy)plan.getStrategy();
        Schedule schedule = strategy.getSchedule();
        schedule.setCronTrigger("* * * * * *");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
    }
    
    private SchedulePlan createSchedulePlan(User user) {
        String planGuid = BridgeUtils.generateGuid();
        
        Schedule schedule = new DynamoSchedule();
        schedule.setStudyAndUser(helper.getTestStudy(), user);
        schedule.setSchedulePlanGuid(planGuid);
        schedule.setLabel("Task AAA");
        schedule.setActivityType(ActivityType.TASK);
        schedule.setActivityRef("task:AAA");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 6 ? * MON-FRI *");
        
        long oneDay = (24 * 60 * 60 * 1000); 
        schedule.setExpires(DateUtils.getCurrentMillisFromEpoch() + oneDay);
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid(planGuid);
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStrategy(strategy);
        plan.setStudyKey(helper.getTestStudy().getKey());
        return plan;
    }

}
