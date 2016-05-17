package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Further tests for these strategy objects are in ScheduleStrategyTest.
 */
public class ABTestScheduleStrategyTest {

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private ArrayList<User> users;
    private Study study;

    @Before
    public void before() {
        study = TestUtils.getValidStudy(ScheduleStrategyTest.class);
        users = Lists.newArrayList();
        for (int i = 0; i < 1000; i++) {
            User user = new User();
            user.setId(Integer.toString(i));
            user.setEmail("test" + i + "@sagebridge.org");
            user.setHealthCode(BridgeUtils.generateGuid());
            user.setStudyKey(study.getIdentifier());
            users.add(user);
        }
    }
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ABTestScheduleStrategy.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void testScheduleCollector() {
        StudyIdentifier studyId = new StudyIdentifierImpl("test-study");
        SchedulePlan plan = TestUtils.getABTestSchedulePlan(studyId);
        
        List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
        assertEquals(3, schedules.size());
        assertEquals("Schedule 1", schedules.get(0).getLabel());
        assertEquals("Schedule 2", schedules.get(1).getLabel());
        assertEquals("Schedule 3", schedules.get(2).getLabel());
        assertTrue(schedules instanceof ImmutableList);
    }
    
    @Test
    public void canRountripABTestingPlan() throws Exception {
        DynamoSchedulePlan plan = createABSchedulePlan();
        String output = MAPPER.writeValueAsString(plan);

        JsonNode node = MAPPER.readTree(output);
        DynamoSchedulePlan newPlan = DynamoSchedulePlan.fromJson(node);
        newPlan.setStudyKey(plan.getStudyKey()); // not serialized.

        assertEquals("Plan with AB testing strategy was serialized/deserialized", plan, newPlan);

        ABTestScheduleStrategy strategy = (ABTestScheduleStrategy) plan.getStrategy();
        ABTestScheduleStrategy newStrategy = (ABTestScheduleStrategy) newPlan.getStrategy();
        assertEquals("Deserialized AB testing strategy is complete", strategy.getScheduleGroups().get(0).getSchedule(),
                        newStrategy.getScheduleGroups().get(0).getSchedule());
    }

    @Test
    public void verifyABTestingStrategyWorks() {
        DynamoSchedulePlan plan = createABSchedulePlan();

        List<Schedule> schedules = Lists.newArrayList();
        for (User user : users) {
            ScheduleContext context = new ScheduleContext.Builder()
                    .withStudyIdentifier(study.getStudyIdentifier())
                    .withHealthCode(user.getHealthCode()).build();
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            schedules.add(schedule);
        }

        // We want 4 in A, 4 in B and 2 in C, and they should not be in order...
        Multiset<String> countsByLabel = HashMultiset.create();
        for (Schedule schedule : schedules) {
            countsByLabel.add(schedule.getLabel());
        }
        assertTrue("40% users assigned to A", Math.abs(countsByLabel.count("A") - 400) < 50);
        assertTrue("40% users assigned to B", Math.abs(countsByLabel.count("B") - 400) < 50);
        assertTrue("20% users assigned to C", Math.abs(countsByLabel.count("C") - 200) < 50);
    }
    
    @Test
    public void validatesNewABTestingPlan() {
        SchedulePlan plan = new DynamoSchedulePlan();
        
        Set<String> dataGroups = Sets.newHashSet("dataGroupA");
        Set<String> taskIdentifiers = Sets.newHashSet("taskIdentifierA");
        
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(20, TestUtils.getSchedule("A Schedule"));
        
        Errors errors = Validate.getErrorsFor(plan);
        strategy.validate(dataGroups, taskIdentifiers, errors);
        Map<String,List<String>> map = Validate.convertErrorsToSimpleMap(errors);
        
        List<String> errorMessages = map.get("scheduleGroups");
        assertEquals("scheduleGroups groups must add up to 100%", errorMessages.get(0));
        errorMessages = map.get("scheduleGroups[0].schedule.expires");
        assertEquals("scheduleGroups[0].schedule.expires must be set if schedule repeats", errorMessages.get(0));
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
        strategy.addGroup(40, TestUtils.getSchedule("A"));
        strategy.addGroup(40, TestUtils.getSchedule("B"));
        strategy.addGroup(20, TestUtils.getSchedule("C"));
        return strategy;
    }

}
