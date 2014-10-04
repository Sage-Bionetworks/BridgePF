package org.sagebionetworks.bridge.services;

import static org.junit.Assert.*;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
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
    TestUserAdminHelper helper;
    
    @Resource
    ScheduleChangeListener listener;
    
    @Resource
    DynamoScheduleDao scheduleDao;
    
    @Resource
    DynamoSchedulePlanDao schedulePlanDao;
    
    private Study study = TestConstants.SECOND_STUDY;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoSchedule.class, DynamoSchedulePlan.class);
        DynamoTestUtil.clearTable(DynamoSchedule.class, "schedulePlanGuid", "data", "expires");
        DynamoTestUtil.clearTable(DynamoSchedulePlan.class, "version", "modifiedOn", "strategy");
    }
    
    // This won't fully work until we're gathering a list of users who are enrolled in the study,
    // not a list of all users who signed up under the study. Right now, enrollment does not 
    // change the list of users for the study.
    @Test
    @Ignore
    public void addPlanThenEnrollUnenrollUser() throws Exception {
        UserSession session = null;
        User user = null;
        try {
            session = helper.createUser(new TestUser("enrollme", "enrollme@sagebridge.org", "P4ssword"), null, study, true, false);
            user = session.getUser();
            
            SchedulePlan plan = createSchedulePlan(user);
            schedulePlanDao.createSchedulePlan(plan);
            
            List<Schedule> schedules = scheduleDao.getSchedules(study, user);
            assertEquals("No schedules because the user hasn't joined the study", 0, schedules.size());
            
            listener.onTestEvent(new UserEnrolledEvent(user, study));
            
            schedules = scheduleDao.getSchedules(study, user);
            assertEquals("User joined study and has a schedule", 1, schedules.size());
            
            listener.onTestEvent(new UserUnenrolledEvent(user, study));
            
            schedules = scheduleDao.getSchedules(study, user);
            assertEquals("User left study and has no schedules", 0, schedules.size());
        } finally {
            helper.deleteUser(session);
        }
    }
    
    @Test
    public void addUserThenCrudPlan() throws Exception {
        UserSession session = null;
        try {
            session = helper.createUser();
            
            List<Schedule> schedules = scheduleDao.getSchedules(study, session.getUser());
            assertEquals("No schedules because there's no plan", 0, schedules.size());
            
            SchedulePlan plan = createSchedulePlan(session.getUser());
            listener.onTestEvent(new SchedulePlanCreatedEvent(plan));

            // Now there is a schedule for our dude(tte)
            schedules = scheduleDao.getSchedules(study, session.getUser());
            assertEquals("There is now one schedule for the user", 1, schedules.size());
            
            updateSchedulePlan(plan);
            listener.onTestEvent(new SchedulePlanUpdatedEvent(plan));
            
            schedules = scheduleDao.getSchedules(study, session.getUser());
            assertEquals("There is still one schedule for the user", 1, schedules.size());
            assertEquals("That schedule shows an update", "* * * * * *", schedules.get(0).getSchedule());
            
            listener.onTestEvent(new SchedulePlanDeletedEvent(plan));
            schedules = scheduleDao.getSchedules(study, session.getUser());
            assertEquals("Now there is no schedule after the one plan was deleted", 0, schedules.size());
            
        } finally {
            helper.deleteUser(session);
        }
    }
    
    private void updateSchedulePlan(SchedulePlan plan) {
        SimpleScheduleStrategy strategy = (SimpleScheduleStrategy)plan.getStrategy();
        Schedule schedule = strategy.getSchedule();
        schedule.setSchedule("* * * * * *");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
    }
    
    private SchedulePlan createSchedulePlan(User user) {
        String planGuid = BridgeUtils.generateGuid();
        
        Schedule schedule = new DynamoSchedule();
        schedule.setStudyAndUser(study, user);
        schedule.setSchedulePlanGuid(planGuid);
        schedule.setLabel("Task AAA");
        schedule.setActivityType(ActivityType.TASK);
        schedule.setActivityRef("task:AAA");
        schedule.setScheduleType(ScheduleType.CRON);
        schedule.setSchedule("0 0 6 ? * MON-FRI *");
        schedule.setExpires(DateUtils.getCurrentMillisFromEpoch() + (24 * 60 * 60 * 1000));
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid(planGuid);
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStrategy(strategy);
        plan.setStudyKey(TestConstants.SECOND_STUDY.getKey());
        return plan;
    }

}
