package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoScheduleDaoTest {

    ObjectMapper mapping = new ObjectMapper();
    
    @Resource
    DynamoScheduleDao scheduleDao;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoSchedule.class);
        DynamoTestUtil.clearTable(DynamoSchedule.class);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void testInvalidScheduleIsRejected() {
        Study study = TestConstants.SECOND_STUDY;

        User user = new User();
        user.setId("test-user");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("test-plan-id");

        Schedule schedule = createSchedule(study, user, plan, "Patient Assessment of Chronic Illness Care Survey",
                "http://bridge-uat.herokuapp.com/api/v1/surveys/ecf7e761-c7e9-4bb6-b6e7-d6d15c53b209/2014-09-25T20:07:49.186Z");
        schedule.setScheduleType(ScheduleType.ONCE);
        
        List<Schedule> list = Lists.newArrayList(schedule);
        
        scheduleDao.createSchedules(list);
    }

    @Test
    public void crudSchedules() {
        Study study = TestConstants.SECOND_STUDY;

        User user = new User();
        user.setId("test-user");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("test-plan-id");

        List<Schedule> list = Lists.newArrayList();
        list.add(createSchedule(study, user, plan, "Patient Assessment of Chronic Illness Care Survey",
                "http://bridge-uat.herokuapp.com/api/v1/surveys/ecf7e761-c7e9-4bb6-b6e7-d6d15c53b209/2014-09-25T20:07:49.186Z"));
        list.add(createSchedule(study, user, plan, "Parkinson’s Disease Quality of Life Questionnaire",
                "http://bridge-uat.herokuapp.com/api/v1/surveys/e7e8b5c7-16b6-412d-bcf9-f67291781972/2014-09-25T20:07:50.794Z"));

        scheduleDao.createSchedules(list);
        
        List<Schedule> newList = scheduleDao.getSchedules(study, user);
        assertEquals("Both schedules were saved", 2, newList.size());
        
        scheduleDao.deleteSchedules(plan);
        
        newList = scheduleDao.getSchedules(study, user);
        assertEquals("Both schedules are deleted", 0, newList.size());
    }
    
    private Schedule createSchedule(Study study, User user, DynamoSchedulePlan plan, String name, String url) {
        Schedule schedule = new DynamoSchedule();
        schedule.setStudyAndUser(study, user);
        schedule.setSchedulePlanGuid(plan.getGuid());
        schedule.setLabel(name);
        schedule.setActivityType(ActivityType.SURVEY);
        schedule.setActivityRef(url);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("* * * * * *");
        schedule.setStartsOn(DateUtils.getCurrentMillisFromEpoch());
        schedule.setEndsOn(DateUtils.getCurrentMillisFromEpoch() + (3 * 24 * 60 * 60 * 1000));
        return schedule;
    }
    
}
