package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.Study;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ScheduleStrategyTest {

    private static final String SURVEY_URL = "http://sagebridge.org/surveys/ABC/2015-01-27T00:38:32.486Z";
    
    private BridgeObjectMapper mapper = BridgeObjectMapper.get();
    private ArrayList<User> users;
    private Study study;
    
    @Before
    public void before() {
        users = Lists.newArrayList();
        for (int i=0; i < 1000; i++) {
        	User user = new User(Integer.toString(i), "test"+i+"@sagebridge.org");
        	user.setHealthCode(BridgeUtils.generateGuid());
            users.add(user);
        }
        study = new DynamoStudy();
        study.setName("name");
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        study.setMinAgeOfConsent(18);
    }
    
    @Test
    public void canRountripSimplePlan() throws Exception {
        Schedule schedule = createSchedule("AAA");
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(study.getIdentifier());
        plan.setStrategy(strategy);
        
        String output = JsonUtils.toJSON(plan);
        JsonNode node = mapper.readTree(output);
        DynamoSchedulePlan newPlan = DynamoSchedulePlan.fromJson(node);
        
        assertEquals("Plan with simple strategy was serialized/deserialized", plan, newPlan);
        
        SimpleScheduleStrategy newStrategy = (SimpleScheduleStrategy)newPlan.getStrategy();
        assertEquals("Deserialized simple testing strategy is complete", strategy.getSchedule(), newStrategy.getSchedule());
    }

    @Test
    public void canRountripABTestingPlan() throws Exception {
        DynamoSchedulePlan plan = createABSchedulePlan();
        String output = JsonUtils.toJSON(plan);
        
        JsonNode node = mapper.readTree(output);
        DynamoSchedulePlan newPlan = DynamoSchedulePlan.fromJson(node);
        
        assertEquals("Plan with AB testing strategy was serialized/deserialized", plan, newPlan);
        
        ABTestScheduleStrategy strategy = (ABTestScheduleStrategy)plan.getStrategy();
        ABTestScheduleStrategy newStrategy = (ABTestScheduleStrategy)newPlan.getStrategy();
        assertEquals("Deserialized AB testing strategy is complete", strategy.getScheduleGroups().get(0).getSchedule(),
                newStrategy.getScheduleGroups().get(0).getSchedule());
    }
    
    @Test
    public void verifyABTestingStrategyWorks() {
        DynamoSchedulePlan plan = createABSchedulePlan();

        List<Schedule> schedules = Lists.newArrayList();
        for (User user : users) {
        	Schedule schedule = plan.getStrategy().getScheduleForUser(study, plan, user);
        	schedules.add(schedule);
        }
        
        // We want 4 in A, 4 in B and 2 in C
        // and they should not be in order...
        Map<String,Integer> countsByLabel = Maps.newHashMap();
        for (Schedule schedule : schedules) {
            Integer count = countsByLabel.get(schedule.getLabel());
            if (count == null) {
                countsByLabel.put(schedule.getLabel(), 1);
            } else {
                countsByLabel.put(schedule.getLabel(), ++count);
            }
        }
        assertTrue("40% users assigned to A", Math.abs(countsByLabel.get("A").intValue()-400) < 50);
        assertTrue("40% users assigned to B", Math.abs(countsByLabel.get("B").intValue()-400) < 50);
        assertTrue("20% users assigned to C", Math.abs(countsByLabel.get("C").intValue()-200) < 50);
    }
    
    @Test
    public void canSendOldJsonAndCreateValidScheduleV2() throws Exception {
        String json = "{\"label\":\"new variant\",\"cronTrigger\":\"0 */2 * * *\",\"scheduleType\":\"recurring\",\"activityRef\":\"https://parkinson-staging.sagebridge.org/api/v1/surveys/260666a8-e9fe-46df-8832-75d3e4f7448e/2014-12-04T18:35:46.587Z\",\"activityType\":\"survey\"}";
        
        Schedule schedule = mapper.readValue(json, Schedule.class);
        Activity activity = schedule.getActivities().get(0);
        assertEquals("https://parkinson-staging.sagebridge.org/api/v1/surveys/260666a8-e9fe-46df-8832-75d3e4f7448e/2014-12-04T18:35:46.587Z", activity.getRef());
        assertEquals(ActivityType.SURVEY, activity.getActivityType());
        
        SurveyReference ref = activity.getSurvey();
        assertEquals("260666a8-e9fe-46df-8832-75d3e4f7448e", ref.getGuid());
        assertEquals("2014-12-04T18:35:46.587Z", ref.getCreatedOn());
        
        GuidCreatedOnVersionHolder keys = activity.getGuidCreatedOnVersionHolder();
        assertEquals("260666a8-e9fe-46df-8832-75d3e4f7448e", keys.getGuid());
        assertEquals(DateTime.parse("2014-12-04T18:35:46.587Z"), keys.getCreatedOn());
    }
    
    @Test
    @Ignore
    public void canRequestScheduleV2AndStillGetOldFields() throws Exception {
        Schedule schedule = createSchedule("new variant");
        
        String json = mapper.writeValueAsString(schedule);
        
        schedule = mapper.readValue(json, Schedule.class);
        
        //assertEquals(SURVEY_URL, schedule.getActivityRef());
        //assertEquals(ActivityType.SURVEY, schedule.getActivityType());
    }
    
    private DynamoSchedulePlan createABSchedulePlan() {
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        // plan.setGuid("a71eecc3-5e75-4a11-91f4-c587999cbb20");
        plan.setGuid(BridgeUtils.generateGuid());
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(study.getIdentifier());
        plan.setStrategy(createABTestStrategy());
        return plan;
    }

    private ABTestScheduleStrategy createABTestStrategy() {
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(40, createSchedule("A"));
        strategy.addGroup(40, createSchedule("B"));
        strategy.addGroup(20, createSchedule("C"));
        return strategy;
    }
    
    private Schedule createSchedule(String label) {
        Schedule schedule = new Schedule();
        schedule.setLabel(label);
        schedule.addActivity(new Activity("Test survey", SURVEY_URL));
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 */2 * * *");
        return schedule;
    }

}
