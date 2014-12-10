package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestUtils.waitFor;

import java.util.List;
import java.util.concurrent.Callable;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedule;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduleDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlanDao;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.events.UserEnrolledEvent;
import org.sagebionetworks.bridge.events.UserUnenrolledEvent;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
@Ignore // Sadly, this still doesn't pass on Travis. Don't know why.
public class ScheduleChangeListenerTest {
    
    private static final String CRON_TRIGGER = "* * * * * *";

    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private ScheduleChangeListener listener;
    
    @Resource
    private DynamoScheduleDao scheduleDao;
    
    @Resource
    private DynamoSchedulePlanDao schedulePlanDao;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoSchedule.class, DynamoSchedulePlan.class);
        DynamoTestUtil.clearTable(DynamoSchedule.class);
        DynamoTestUtil.clearTable(DynamoSchedulePlan.class);
    }
    
    @Test
    public void addPlanThenEnrollUnenrollUser() throws Exception {
        TestUser testUser = helper.createUser(ScheduleChangeListenerTest.class, true, false); // not consented
        final User user = testUser.getUser();
        final Study study = testUser.getStudy();
        SchedulePlan plan = null;
        try {
            plan = createSchedulePlan(study, user);
            
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
            schedulePlanDao.deleteSchedulePlan(study, plan.getGuid());
            helper.deleteUser(testUser);
        }
    }
    
    @Test
    public void addUserThenCrudPlan() throws Exception {
        TestUser testUser = helper.createUser(ScheduleChangeListenerTest.class);
        final Study study = testUser.getStudy();
        final User user = testUser.getUser();
        SchedulePlan plan = null;
        boolean deleted = false;
        try {
            List<Schedule> schedules = scheduleDao.getSchedules(study, user);
            assertEquals("No schedules because there's no plan", 0, schedules.size());

            plan = createSchedulePlan(study, user);
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return (scheduleDao.getSchedules(study, user).size() == 1);
                }
            });
            schedules = scheduleDao.getSchedules(study, user);
            assertEquals("There is now one schedule for the user", 1, schedules.size());

            updateSchedulePlan(plan);
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    List<Schedule> list = scheduleDao.getSchedules(study, user);
                    return (!list.isEmpty() && list.size() == 1 && CRON_TRIGGER.equals(list.get(0).getCronTrigger()));
                }
            });
            schedules = scheduleDao.getSchedules(study, user);
            assertEquals("There is still one schedule for the user", 1, schedules.size());
            assertEquals("That schedule shows an update", CRON_TRIGGER, schedules.get(0).getCronTrigger());

            schedulePlanDao.deleteSchedulePlan(study, plan.getGuid());
            deleted = true;
            waitFor(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return (scheduleDao.getSchedules(study, user).size() == 0);
                }
            });
            schedules = scheduleDao.getSchedules(study, user);
            assertEquals("Now there is no schedule after the one plan was deleted", 0, schedules.size());
            
        } finally {
            if (!deleted) {
                schedulePlanDao.deleteSchedulePlan(study, plan.getGuid());    
            }
            helper.deleteUser(testUser);
        }
    }
    
    private void updateSchedulePlan(SchedulePlan plan) {
        SimpleScheduleStrategy strategy = (SimpleScheduleStrategy)plan.getStrategy();
        Schedule schedule = strategy.getSchedule();
        schedule.setCronTrigger(CRON_TRIGGER);
        schedulePlanDao.updateSchedulePlan(plan);
    }
    
    private SchedulePlan createSchedulePlan(Study study, User user) {
        String planGuid = BridgeUtils.generateGuid();
        
        DynamoSchedule schedule = new DynamoSchedule();
        schedule.setStudyAndUser(study, user);
        schedule.setSchedulePlanGuid(planGuid);
        schedule.setLabel("Task AAA");
        schedule.addActivity(new Activity(ActivityType.TASK, "task:AAA"));
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
        plan.setStudyKey(study.getIdentifier());
        schedulePlanDao.createSchedulePlan(plan);
        return plan;
    }

}
