package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy.ScheduleCriteria;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.Study;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

/**
 * Verify that the combination of running the scheduler, and persistence, work 
 * together correctly.   
 */
@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ScheduledActivityServiceTest {

    @Resource
    private ScheduledActivityService service;
    
    @Resource
    private StudyService studyService;
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private SchedulePlanService schedulePlanService;
    
    private SchedulePlan plan;
    
    private Study study;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        study = studyService.getStudy(TEST_STUDY.getIdentifier());
        study.setTaskIdentifiers(Sets.newHashSet("taskId"));
        study.setDataGroups(Sets.newHashSet("group1"));
        testUser = helper.createUser(ScheduledActivityServiceTest.class);
    }

    @After
    public void after() {
        schedulePlanService.deleteSchedulePlan(TEST_STUDY, plan.getGuid());
        if (testUser != null) {
            helper.deleteUser(study, testUser.getEmail());
        }
    }
    
    private void createSimpleSchedulePlan() {
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule Label");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("P1D");
        schedule.addTimes("10:00");
        schedule.addActivity(new Activity.Builder().withLabel("label").withTask("taskId").build());
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy(); 
        strategy.setSchedule(schedule);
        
        plan = new DynamoSchedulePlan();
        plan.setLabel("Label");
        plan.setStudyKey(TEST_STUDY.getIdentifier());
        plan.setMinAppVersion(10);
        plan.setStrategy(strategy);
        plan = schedulePlanService.createSchedulePlan(study, plan);
    }
    
    private void createCriteriaSchedulePlan() {
        plan = new DynamoSchedulePlan();

        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule Label 2");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("P1D");
        schedule.addTimes("10:00");
        schedule.addActivity(new Activity.Builder().withLabel("label").withTask("taskId").build());
        
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        ScheduleCriteria crit = new CriteriaScheduleStrategy.ScheduleCriteria.Builder()
            .withSchedule(schedule)
            .addRequiredGroup("group1")
            .withMinAppVersion(2)
            .withMaxAppVersion(10)
            .build();
        strategy.addCriteria(crit);
        
        plan = new DynamoSchedulePlan();
        plan.setLabel("Label 2");
        plan.setStudyKey(TEST_STUDY.getIdentifier());
        plan.setStrategy(strategy);
        plan = schedulePlanService.createSchedulePlan(study, plan);
    }
    
    @Test
    public void retrievalActivitiesAcrossTimeAndTimeZones() throws Exception {
        createSimpleSchedulePlan();
        
        // We start this test in the early morning in Russia, in the future so the new user's
        // enrollment doesn't screw up the test.
        int year = DateTime.now().getYear();
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse((year+1)+"-09-23T03:39:57.779+03:00").getMillis());

        // These time zones are far apart and for our chosen time, Dave will be teleporting to the 
        // previous day. Our scheduler must do something rational.
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        DateTimeZone PST = DateTimeZone.forOffsetHours(-7);

        // Anticipated schedule times in Russia (exact seconds not important)
        String msk0 = DateTime.now(MSK).minusDays(1).toLocalDate().toString(); // this is yesterdays activity, not expired yet 
        String msk1 = DateTime.now(MSK).toLocalDate().toString();
        String msk2 = DateTime.now(MSK).plusDays(1).toLocalDate().toString();
        String msk3 = DateTime.now(MSK).plusDays(2).toLocalDate().toString();
        String msk4 = DateTime.now(MSK).plusDays(3).toLocalDate().toString();
        
        // Anticipated schedule times in California (exact seconds not important)
        String pst1 = DateTime.now(PST).toLocalDate().toString();
        String pst2 = DateTime.now(PST).plusDays(1).toLocalDate().toString();
        String pst3 = DateTime.now(PST).plusDays(2).toLocalDate().toString();
        // Never returned... though it exists
        // String pst4 = DateTime.now(PST).plusDays(3).toLocalDate().toString();
        
        // Hi, I'm dave, I'm in Moscow, what am I supposed to do for the next two days?
        // You get the schedule from yesterday that hasn't expired just yet (22nd), plus the 
        // 23rd, 24th and 25th
        ScheduleContext context = getContextWith2DayWindow(MSK);
        List<ScheduledActivity> activities = service.getScheduledActivities(testUser.getUser(), context);
        assertEquals(4, activities.size());
        assertEquals(msk0+"T10:00:00.000+03:00", activities.get(0).getScheduledOn().toString());
        assertEquals(msk1+"T10:00:00.000+03:00", activities.get(1).getScheduledOn().toString());
        assertEquals(msk2+"T10:00:00.000+03:00", activities.get(2).getScheduledOn().toString());
        assertEquals(msk3+"T10:00:00.000+03:00", activities.get(3).getScheduledOn().toString());
        
        // Dave teleports to California, where it's still the prior day. He gets 4 activities 
        // (yesterday, today in Russia, tomorrow and the next day). One activity was created beyond
        // the window, over in Moscow... that is not returned because although it exists, we 
        // filter it out from the persisted activities retrieved from the db.
        activities = service.getScheduledActivities(testUser.getUser(), getContextWith2DayWindow(PST));
        assertEquals(3, activities.size());
        assertEquals(pst1+"T10:00:00.000-07:00", activities.get(0).getScheduledOn().toString());
        assertEquals(pst2+"T10:00:00.000-07:00", activities.get(1).getScheduledOn().toString());
        assertEquals(pst3+"T10:00:00.000-07:00", activities.get(2).getScheduledOn().toString());
        
        // Dave returns to the Moscow and we move time forward a day.
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse((year+1)+"-09-24T03:39:57.779+03:00").getMillis());
        
        // He hasn't finished any activities. The 22nd expires but it's too early in the day 
        // for the 23rd to expire (earlier than 10am), so, 4 activities, but with different dates.
        activities = service.getScheduledActivities(testUser.getUser(), getContextWith2DayWindow(MSK));
        assertEquals(4, activities.size());
        assertEquals(msk1+"T10:00:00.000+03:00", activities.get(0).getScheduledOn().toString());
        assertEquals(msk2+"T10:00:00.000+03:00", activities.get(1).getScheduledOn().toString());
        assertEquals(msk3+"T10:00:00.000+03:00", activities.get(2).getScheduledOn().toString());
        assertEquals(msk4+"T10:00:00.000+03:00", activities.get(3).getScheduledOn().toString());
        
        // Dave, please finish some activities... 
        activities.get(0).setFinishedOn(DateTime.now().getMillis());
        activities.get(1).setFinishedOn(DateTime.now().getMillis());
        service.updateScheduledActivities(testUser.getUser().getHealthCode(), activities);
        
        // This is easy, Dave has the later activities and that's it, at this point.
        activities = service.getScheduledActivities(testUser.getUser(), getContextWith2DayWindow(MSK));
        assertEquals(2, activities.size());
        assertEquals(msk3+"T10:00:00.000+03:00", activities.get(0).getScheduledOn().toString());
        assertEquals(msk4+"T10:00:00.000+03:00", activities.get(1).getScheduledOn().toString());
    }
    
    @Test
    public void activitiesAreFilteredBasedOnAppVersion() throws Exception {
        createSimpleSchedulePlan();
        
        ScheduleContext contextV5 = new ScheduleContext.Builder()
            .withContext(getContextWith2DayWindow(DateTimeZone.UTC))
            .withClientInfo(ClientInfo.fromUserAgentCache("app/5")).build();
        
        ScheduleContext contextV11 = new ScheduleContext.Builder()
            .withContext(getContextWith2DayWindow(DateTimeZone.UTC))
            .withClientInfo(ClientInfo.fromUserAgentCache("app/11")).build();
        
        // Ask for version 5, nothing is created
        List<ScheduledActivity> activities = service.getScheduledActivities(testUser.getUser(), contextV5);
        assertEquals(0, activities.size());
        
        // Ask for version 11, normal activities are created.
        activities = service.getScheduledActivities(testUser.getUser(), contextV11);
        assertEquals(3, activities.size());
        
        // Now go back to version 5 where there should be nothing and ask again... once again since nothing 
        // was started, there should be no tasks
        activities = service.getScheduledActivities(testUser.getUser(), contextV5);
        assertEquals(0, activities.size());
        
        // Now get some under 11, start one, ask again as a version 5, you should just get that one started task
        activities = service.getScheduledActivities(testUser.getUser(), contextV11);
        activities.get(0).setStartedOn(DateTime.now().getMillis());
        
        service.updateScheduledActivities(contextV11.getHealthCode(), activities);
        
        activities = service.getScheduledActivities(testUser.getUser(), contextV5);
        assertEquals(1, activities.size());
    }
    
    @Test
    public void persistedActivitiesAreFilteredByEndsOn() throws Exception {
        createSimpleSchedulePlan();
        
        // This was demonstrated above, but by only one activity... this is a more exaggerated test
        // Four days...
        DateTime endsOn = DateTime.now().plusDays(4);
        ScheduleContext context = getContext(DateTimeZone.UTC, endsOn);
        List<ScheduledActivity> activities = service.getScheduledActivities(testUser.getUser(), context);
        
        // Zero days... there are fewer activities
        endsOn = DateTime.now().plusDays(0);
        context = getContext(DateTimeZone.UTC, endsOn);
        List<ScheduledActivity> activities2 = service.getScheduledActivities(testUser.getUser(), context);
        
        assertTrue(activities2.size() < activities.size());
    }

    @Test
    public void activitiesAreFilteredOnDataGroups() {
        createCriteriaSchedulePlan();
        
        // Does not have the required group. 
        ScheduleContext context = new ScheduleContext.Builder()
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withEndsOn(DateTime.now().plusDays(2))
            .withUser(testUser.getUser())
            .withTimeZone(DateTimeZone.UTC)
            .build();
        // So nothing found
        List<ScheduledActivity> activities = service.getScheduledActivities(testUser.getUser(), context);
        assertTrue(activities.isEmpty());
        
        // Adding the group, we should get something back;
        testUser.getUser().getDataGroups().add("group1");
        activities = service.getScheduledActivities(testUser.getUser(), context);
        assertFalse(activities.isEmpty());
        
        // We can go back: without the group, we don't see the task
        testUser.getUser().getDataGroups().clear();
        activities = service.getScheduledActivities(testUser.getUser(), context);
        assertTrue(activities.isEmpty());
        
        // Restore (and verify) that the user can get activities for the next tests
        testUser.getUser().getDataGroups().add("group1");
        activities = service.getScheduledActivities(testUser.getUser(), context);
        assertFalse(activities.isEmpty());
        
        // Now filter on app version too high
        context = new ScheduleContext.Builder()
            .withContext(context)
            .withClientInfo(ClientInfo.fromUserAgentCache("app/12"))
            .build();
        activities = service.getScheduledActivities(testUser.getUser(), context);
        assertTrue(activities.isEmpty());
        
        // filter on app version too low
        context = new ScheduleContext.Builder()
            .withContext(context)
            .withClientInfo(ClientInfo.fromUserAgentCache("app/1"))
            .build();
        activities = service.getScheduledActivities(testUser.getUser(), context);
        assertTrue(activities.isEmpty());
        
        context = new ScheduleContext.Builder()
            .withContext(context)
            .withClientInfo(ClientInfo.fromUserAgentCache("app/4"))
            .build();
        activities = service.getScheduledActivities(testUser.getUser(), context);
        assertFalse(activities.isEmpty());
    }
    
    private ScheduleContext getContextWith2DayWindow(DateTimeZone zone) {
        return getContext(zone, DateTime.now(zone).plusDays(2));
    }
    
    private ScheduleContext getContext(DateTimeZone zone, DateTime endsOn) {
        // Setting the endsOn value to the end of the day, as we do in the controller.
        return new ScheduleContext.Builder()
            .withUser(testUser.getUser())
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(zone)
            // Setting the endsOn value to the end of the day, as we do in the controller.
            .withEndsOn(endsOn.withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59)).build();
    }
}
