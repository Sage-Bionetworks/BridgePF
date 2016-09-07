package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;

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
        
        ScheduleStrategy strategy = TestUtils.getStrategy("P1D", TestConstants.TEST_1_ACTIVITY);
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Label");
        plan.setGuid("guid");
        plan.setModifiedOn(datetime.getMillis());
        plan.setStudyKey("test-study");
        plan.setVersion(2L);
        plan.setStrategy(strategy);
        
        String json = BridgeObjectMapper.get().writeValueAsString(plan);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("SchedulePlan", node.get("type").asText());
        assertEquals(2, node.get("version").asInt());
        assertEquals("guid", node.get("guid").asText());
        assertEquals("Label", node.get("label").asText());
        assertNull(node.get("studyKey"));
        assertNotNull(node.get("strategy"));
        assertEquals(datetime, DateTime.parse(node.get("modifiedOn").asText()));

        DynamoSchedulePlan plan2 = DynamoSchedulePlan.fromJson(node);
        assertEquals(plan.getVersion(), plan2.getVersion());
        assertEquals(plan.getGuid(), plan2.getGuid());
        assertEquals(plan.getLabel(), plan2.getLabel());
        assertEquals(plan.getModifiedOn(), plan2.getModifiedOn());
        
        ScheduleStrategy retrievedStrategy = plan.getStrategy();
        assertEquals(retrievedStrategy, strategy);
    }
    
    @Test
    public void jsonStudyKeyIsIgnored() throws Exception {
        String json = TestUtils.createJson("{'studyKey':'study-key'}");
        
        SchedulePlan plan = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        assertNull(plan.getStudyKey());
        
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        plan = DynamoSchedulePlan.fromJson(node);
        assertNull(plan.getStudyKey());
    }
    
}
