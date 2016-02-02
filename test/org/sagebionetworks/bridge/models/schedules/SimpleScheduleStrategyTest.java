package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Further tests for these strategy objects are in ScheduleStrategyTest.
 */
public class SimpleScheduleStrategyTest {

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private Study study;

    @Before
    public void before() {
        study = TestUtils.getValidStudy(ScheduleStrategyTest.class);
    }

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SimpleScheduleStrategy.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }

    @Test
    public void canRountripSimplePlan() throws Exception {
        Schedule schedule = TestUtils.getSchedule("AAA");
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);

        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(study.getIdentifier());
        plan.setStrategy(strategy);

        String output = MAPPER.writeValueAsString(plan);
        JsonNode node = MAPPER.readTree(output);
        DynamoSchedulePlan newPlan = DynamoSchedulePlan.fromJson(node);

        assertEquals("Plan with simple strategy was serialized/deserialized", plan, newPlan);

        SimpleScheduleStrategy newStrategy = (SimpleScheduleStrategy) newPlan.getStrategy();
        assertEquals("Deserialized simple testing strategy is complete", strategy.getSchedule(),
                        newStrategy.getSchedule());
    }
    
    @Test
    public void validates() {
        SchedulePlan plan = new DynamoSchedulePlan();
        
        Set<String> dataGroups = Sets.newHashSet("dataGroupA");
        Set<String> taskIdentifiers = Sets.newHashSet("taskIdentifierA");
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(TestUtils.getSchedule("A Schedule"));
        
        Errors errors = Validate.getErrorsFor(plan);
        strategy.validate(dataGroups, taskIdentifiers, errors);
        Map<String,List<String>> map = Validate.convertErrorsToSimpleMap(errors);
        
        List<String> errorMessages = map.get("schedule.expires");
        assertEquals("schedule.expires must be set if schedule repeats", errorMessages.get(0));
    }
    
    @Test
    public void testScheduleCollector() {
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(TEST_STUDY);
        
        List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
        assertEquals(1, schedules.size());
        assertEquals("Test label for the user", schedules.get(0).getLabel());
        assertTrue(schedules instanceof ImmutableList);
    }
}
