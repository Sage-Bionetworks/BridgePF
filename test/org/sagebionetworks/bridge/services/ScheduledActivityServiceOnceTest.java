package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ScheduledActivityServiceOnceTest {
    private static DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
    private static DateTimeZone PST = DateTimeZone.forOffsetHours(-7);

    @Resource
    private ScheduledActivityService service;
    
    @Resource
    private StudyService studyService;
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private SchedulePlanService schedulePlanService;
    
    private Schedule schedule;
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

        testUser = helper.getBuilder(this.getClass()).build();

        schedule = new Schedule();
        schedule.setLabel("Schedule Label");
        schedule.setScheduleType(ScheduleType.ONCE);
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
    public void onetimeTasksScheduledCorrectlyWithTimePortionThroughTimezoneChange() {
        schedule.addTimes(LocalTime.parse("13:11"));
        schedulePlanService.updateSchedulePlan(study, schedulePlan);
        
        List<ScheduledActivity> first = service.getScheduledActivities(getContextWith2DayAdvance(PST));
        List<ScheduledActivity> second = service.getScheduledActivities(getContextWith2DayAdvance(MSK));
        assertEquals(1, first.size());
        assertEquals(1, second.size());
        
        DynamoScheduledActivity dynAct1 = (DynamoScheduledActivity)first.get(0);
        DynamoScheduledActivity dynAct2 = (DynamoScheduledActivity)second.get(0);
        
        // The time portion should 13:11 because that's what we set, regardless of time zone.
        assertEquals("13:11:00.000", dynAct1.getLocalScheduledOn().toLocalTime().toString());
        assertEquals("13:11:00.000", dynAct2.getLocalScheduledOn().toLocalTime().toString());
    }
    
    private ScheduleContext getContextWith2DayAdvance(DateTimeZone zone) {
        return new ScheduleContext.Builder()
            .withStudyIdentifier(study.getStudyIdentifier())
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withRequestTimeZone(zone)
            .withAccountCreatedOn(DateTime.now())
            // Setting the endsOn value to the end of the day, as we do in the controller.
            .withEndsOn(DateTime.now(zone).plusDays(2).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59))
            .withHealthCode(testUser.getHealthCode())
            .withUserId(testUser.getId()).build();
    }
}
