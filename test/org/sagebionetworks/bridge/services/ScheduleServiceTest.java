package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedule;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ScheduleServiceTest {
    
    @Resource
    StudyServiceImpl studyService;
    
    @Resource
    ScheduleServiceImpl scheduleService;
    
    private Study study;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoSchedule.class);
        DynamoTestUtil.clearTable(DynamoSchedule.class);
        study = studyService.getStudyByIdentifier(TEST_STUDY_IDENTIFIER);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void testInvalidScheduleIsRejected() {
        User user = new User();
        user.setId("test-user");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("test-plan-id");

        Schedule schedule = createSchedule(study, user, plan, "Patient Assessment of Chronic Illness Care Survey",
                "http://bridge-uat.herokuapp.com/api/v1/surveys/ecf7e761-c7e9-4bb6-b6e7-d6d15c53b209/2014-09-25T20:07:49.186Z");
        schedule.setScheduleType(ScheduleType.ONCE);
        
        List<Schedule> list = Lists.newArrayList(schedule);
        
        scheduleService.createSchedules(list);
    }
    
    // TODO: Maybe refactor out to utility class, it's also used in ScheduleDao tests.
    private Schedule createSchedule(Study study, User user, DynamoSchedulePlan plan, String name, String url) {
        DynamoSchedule schedule = new DynamoSchedule();
        schedule.setStudyAndUser(study, user);
        schedule.setSchedulePlanGuid(plan.getGuid());
        schedule.setLabel(name);
        schedule.addActivity(new Activity(ActivityType.SURVEY, url));
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("* * * * * *");
        schedule.setStartsOn(DateUtils.getCurrentMillisFromEpoch());
        schedule.setEndsOn(DateUtils.getCurrentMillisFromEpoch() + (3 * 24 * 60 * 60 * 1000));
        return schedule;
    }

}
