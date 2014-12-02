package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedule;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.Study;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ScheduleStrategyTest {

    private ObjectMapper mapper = new ObjectMapper();
    private ArrayList<User> users;
    private Study study;
    
    /*
    private String[] GUIDS = new String[] {
        "407d352a-2fdb-4678-8cd5-3f41422aec1f",
        "2943f6cd-6850-4949-b319-acb5680aa21c",
        "eef13b6b-9de2-4516-aa0b-08111051157a",
        "3970a71d-dbcf-4ae4-833d-324f547745ab",
        "e85a2345-bcd4-4f82-95f2-74f3c22e50b7",
        "91557fb6-2ed6-4c42-a288-0bebe1fcfba4",
        "758cfb2b-185e-4b62-9fba-e7262a043e8e",
        "c34f496b-e77c-4d2c-bd9c-78f25d651e79",
        "b2443e99-ce3d-4ca5-96de-b1a4273a8331",
        "7b12a440-09bf-4ddd-816a-c01d5865a2f6",
        "53e2433e-1549-4e0d-94b2-b2fc3a4582cb",
        "4f76a520-f5ce-4c9a-8d5d-f01b54254cd0",
        "36e92db4-6a4e-4d39-9861-12184865d838",
        "4f0c4b08-3b5f-4c69-bf1a-0db8ab8981cd", 
        "fd5f77c2-8783-4d5d-b9e1-5ce564ee02b2",
        "76eda01b-6b87-42e3-a4b7-8ae4aca315b6",
        "0ce3a380-91f5-4213-9ca4-327f78656732",
        "4c759121-fe66-4829-be78-67037d49b3bc",
        "aa57b524-16e9-4b33-aea1-df501e012db2",
        "d4e8161b-399d-4ccc-9ef7-3705aeb79c29"};
    */
    @Before
    public void before() {
        users = Lists.newArrayList();
        for (int i=0; i < 1000; i++) {
        	User user = new User(Integer.toString(i), "test"+i+"@sagebridge.org");
        	// user.setHealthCode(GUIDS[i]);
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
        /*
        System.out.println("A: " + countsByLabel.get("A").intValue());
        System.out.println("B: " + countsByLabel.get("B").intValue());
        System.out.println("C: " + countsByLabel.get("C").intValue());
        */
        assertTrue("40% users assigned to A", Math.abs(countsByLabel.get("A").intValue()-400) < 50);
        assertTrue("40% users assigned to B", Math.abs(countsByLabel.get("B").intValue()-400) < 50);
        assertTrue("20% users assigned to C", Math.abs(countsByLabel.get("C").intValue()-200) < 50);
    }

    private DynamoSchedulePlan createABSchedulePlan() {
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("a71eecc3-5e75-4a11-91f4-c587999cbb20");
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
        Schedule schedule = new DynamoSchedule();
        schedule.setActivityType(ActivityType.SURVEY);
        schedule.setLabel(label);
        schedule.setActivityRef("http://sagebridge.org/survey1");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 */2 * * *");
        return schedule;
    }

}
