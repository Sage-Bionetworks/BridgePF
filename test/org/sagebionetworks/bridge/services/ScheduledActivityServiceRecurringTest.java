package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
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
public class ScheduledActivityServiceRecurringTest {
    private static final DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    private static final DateTimeZone EST = DateTimeZone.forOffsetHours(-3);
    private static final DateTime NOW = DateTime.parse("2017-09-23T03:39:57.779-03:00");

    @Resource
    private ScheduledActivityService service;
    
    @Resource
    private StudyService studyService;
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private SchedulePlanService schedulePlanService;
    
    private SchedulePlan schedulePlan;
    
    private Study study;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        // api study is frequently used for manual tests. To get clean tests, create a new study.
        Study studyToCreate = TestUtils.getValidStudy(this.getClass());
        studyToCreate.setExternalIdRequiredOnSignup(false);
        studyToCreate.setExternalIdValidationEnabled(false);
        studyToCreate.setTaskIdentifiers(Sets.newHashSet("taskId"));
        study = studyService.createStudy(studyToCreate);

        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule Label");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.setExpires("P1D");
        schedule.addTimes("10:00");
        schedule.addActivity(new Activity.Builder().withLabel("label").withTask("taskId").build());
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy(); 
        strategy.setSchedule(schedule);
        
        schedulePlan = new DynamoSchedulePlan();
        schedulePlan.setLabel("Label");
        schedulePlan.setStudyKey(study.getIdentifier());
        schedulePlan.setStrategy(strategy);
        schedulePlan = schedulePlanService.createSchedulePlan(study, schedulePlan);
    }

    @After
    public void after() {
        schedulePlanService.deleteSchedulePlan(study.getStudyIdentifier(), schedulePlan.getGuid());
        if (testUser != null) {
            helper.deleteUser(study, testUser.getId());
        }
        if (study != null) {
            studyService.deleteStudy(study.getIdentifier(), true);
        }
    }
    
    @Test
    public void retrievalActivitiesAcrossTimeAndTimeZones() throws Exception {
        DateTime now = NOW; // we are going to change this in the course of the test.
        
        // Use DateTimeUtils to create the user two days in the past, then restore the time
        DateTimeUtils.setCurrentMillisFixed(now.minusDays(2).getMillis());
        testUser = helper.getBuilder(ScheduledActivityServiceRecurringTest.class).build();
        DateTimeUtils.setCurrentMillisSystem();

        // We start this test in the early morning in Russia
        // Anticipated schedule times in Russia (exact seconds not important)
        String msk0 = now.withZone(MSK).minusDays(1).toLocalDate().toString(); // this is yesterdays activity, not expired yet 
        String msk1 = now.withZone(MSK).toLocalDate().toString();
        String msk2 = now.withZone(MSK).plusDays(1).toLocalDate().toString();
        String msk3 = now.withZone(MSK).plusDays(2).toLocalDate().toString();
        String msk4 = now.withZone(MSK).plusDays(3).toLocalDate().toString();
        
        // Anticipated schedule times in California (exact seconds not important)
        String pst1 = now.withZone(PST).toLocalDate().toString();
        String pst2 = now.withZone(PST).plusDays(1).toLocalDate().toString();
        String pst3 = now.withZone(PST).plusDays(2).toLocalDate().toString();
        String pst4 = now.withZone(PST).plusDays(3).toLocalDate().toString();
        
        // Hi, I'm dave, I'm in Moscow, what am I supposed to do for the next two days?
        // You get the schedule from yesterday that hasn't expired just yet (22nd), plus the 
        // 23rd, 24th and 25th
        ScheduleContext context = getContext(now, EST, MSK, 2);
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        assertEquals(4, activities.size());
        assertEquals(msk0+"T10:00:00.000+03:00", activities.get(0).getScheduledOn().toString());
        assertEquals(msk1+"T10:00:00.000+03:00", activities.get(1).getScheduledOn().toString());
        assertEquals(msk2+"T10:00:00.000+03:00", activities.get(2).getScheduledOn().toString());
        assertEquals(msk3+"T10:00:00.000+03:00", activities.get(3).getScheduledOn().toString());
        
        // Dave teleports to California, where it's still the prior day. He gets 4 activities 
        // (yesterday, today in Russia, tomorrow and the next day). One activity was created beyond
        // the window, over in Moscow... that is not returned because although it exists, we 
        // filter it out from the persisted activities retrieved from the db.
        activities = service.getScheduledActivities(getContext(now, EST, PST, 2));

        assertEquals(4, activities.size());
        assertEquals(pst1+"T10:00:00.000-07:00", activities.get(0).getScheduledOn().toString());
        assertEquals(pst2+"T10:00:00.000-07:00", activities.get(1).getScheduledOn().toString());
        assertEquals(pst3+"T10:00:00.000-07:00", activities.get(2).getScheduledOn().toString());
        assertEquals(pst4+"T10:00:00.000-07:00", activities.get(3).getScheduledOn().toString());
        
        // Dave returns to the Moscow and we move time forward a day.
        now = now.plusDays(1).withZone(MSK);
        
        // He hasn't finished any activities. The 22nd expires but it's too early in the day 
        // for the 23rd to expire (earlier than 10am), so, 4 activities, but with different dates.
        activities = service.getScheduledActivities(getContext(now, EST, MSK, 2));
        assertEquals(4, activities.size());
        assertEquals(msk1+"T10:00:00.000+03:00", activities.get(0).getScheduledOn().toString());
        assertEquals(msk2+"T10:00:00.000+03:00", activities.get(1).getScheduledOn().toString());
        assertEquals(msk3+"T10:00:00.000+03:00", activities.get(2).getScheduledOn().toString());
        assertEquals(msk4+"T10:00:00.000+03:00", activities.get(3).getScheduledOn().toString());
        
        // Dave, please finish some activities... 
        activities.get(0).setFinishedOn(DateTime.now().getMillis());
        activities.get(1).setFinishedOn(DateTime.now().getMillis());
        service.updateScheduledActivities(testUser.getHealthCode(), activities);
        
        // This is easy, Dave has the later activities and that's it, at this point.
        activities = service.getScheduledActivities(getContext(now, EST, MSK, 2));
        
        assertEquals(2, activities.size()); //2
        assertEquals(msk3+"T10:00:00.000+03:00", activities.get(0).getScheduledOn().toString());
        assertEquals(msk4+"T10:00:00.000+03:00", activities.get(1).getScheduledOn().toString());
    }
    
    @Test
    public void persistedActivitiesAreFilteredByEndsOn() throws Exception {
        testUser = helper.getBuilder(ScheduledActivityServiceRecurringTest.class).build();

        // This was demonstrated above, but by only one activity... this is a more exaggerated test
        
        // Four days...
        ScheduleContext context = getContext(NOW, DateTimeZone.UTC, EST, 4);
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        // Zero days... there are fewer activities
        context = getContext(NOW, DateTimeZone.UTC, EST, 0);
        List<ScheduledActivity> activities2 = service.getScheduledActivities(context);
        
        assertTrue(activities2.size() < activities.size());
    }
    
    private ScheduleContext getContext(DateTime now, DateTimeZone initialTimeZone, DateTimeZone requestTimeZone, int daysAhead) {
        return new ScheduleContext.Builder()
                .withStudyIdentifier(study.getStudyIdentifier())
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withInitialTimeZone(initialTimeZone)
                .withRequestTimeZone(requestTimeZone)
                .withNow(now.withZone(requestTimeZone))
                .withAccountCreatedOn(now.withZone(requestTimeZone))
                .withDaysAhead(daysAhead)
                .withHealthCode(testUser.getHealthCode())
                .withUserId(testUser.getId()).build();
    }}
