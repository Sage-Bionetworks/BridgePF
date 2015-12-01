package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy.ScheduleCriteria;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoSchedulePlanTest {
    
    @Test
    public void hashEquals() {
        EqualsVerifier.forClass(DynamoSchedulePlan.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerializeDynamoSchedulePlan() throws Exception {
        DateTime datetime = DateTime.now().withZone(DateTimeZone.UTC);

        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
        
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        ScheduleCriteria criteria = new ScheduleCriteria.Builder().withMinAppVersion(1).withMaxAppVersion(10)
                .withSchedule(schedule).build();
        strategy.addCriteria(criteria);
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Label");
        plan.setGuid("guid");
        plan.setMinAppVersion(2);
        plan.setMaxAppVersion(10);
        plan.setModifiedOn(datetime.getMillis());
        plan.setStudyKey("test-study");
        plan.setVersion(2L);
        plan.setStrategy(strategy);
        
        String json = BridgeObjectMapper.get().writeValueAsString(plan);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("SchedulePlan", node.get("type").asText());
        assertEquals(2, node.get("minAppVersion").asInt());
        assertEquals(10, node.get("maxAppVersion").asInt());
        assertEquals(2, node.get("version").asInt());
        assertEquals("guid", node.get("guid").asText());
        assertEquals("Label", node.get("label").asText());
        assertEquals("test-study", node.get("studyKey").asText());
        assertEquals(datetime, DateTime.parse(node.get("modifiedOn").asText()));
        
        DynamoSchedulePlan plan2 = DynamoSchedulePlan.fromJson(node);
        assertEquals(plan.getMinAppVersion(), plan2.getMinAppVersion());
        assertEquals(plan.getMaxAppVersion(), plan2.getMaxAppVersion());
        assertEquals(plan.getVersion(), plan2.getVersion());
        assertEquals(plan.getGuid(), plan2.getGuid());
        assertEquals(plan.getLabel(), plan2.getLabel());
        assertEquals(plan.getModifiedOn(), plan2.getModifiedOn());
        
        assertEquals(plan, plan2);
    }
    
}
