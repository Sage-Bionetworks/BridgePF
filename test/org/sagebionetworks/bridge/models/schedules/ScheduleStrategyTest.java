package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ScheduleStrategyTest {

    @Test
    public void verifyStrategyDoesNotSerializeGetAllPossibleSchedules() throws Exception {
        ScheduleStrategy strategy = TestUtils.getABTestSchedulePlan(TEST_STUDY).getStrategy();
        
        String output = BridgeObjectMapper.get().writeValueAsString(strategy);
        JsonNode node = BridgeObjectMapper.get().readTree(output);
        assertNull(node.get("allPossibleSchedules"));
        assertEquals("ABTestScheduleStrategy", node.get("type").asText());
        assertEquals(3, ((ArrayNode)node.get("scheduleGroups")).size());
    }
    
    @Test
    public void verifyDeserializationOfStrategies() throws Exception {
        ScheduleStrategy strategy = new ABTestScheduleStrategy();
        ScheduleStrategy reconstitutedStrategy = serializeAndDeserialize(strategy, "ABTestScheduleStrategy");
        assertTrue(reconstitutedStrategy instanceof ABTestScheduleStrategy);
        
        strategy = new SimpleScheduleStrategy();
        reconstitutedStrategy = serializeAndDeserialize(strategy, "SimpleScheduleStrategy");
        assertTrue(reconstitutedStrategy instanceof SimpleScheduleStrategy);
        
        strategy = new CriteriaScheduleStrategy();
        reconstitutedStrategy = serializeAndDeserialize(strategy, "CriteriaScheduleStrategy");
        assertTrue(reconstitutedStrategy instanceof CriteriaScheduleStrategy);
    }
    
    private ScheduleStrategy serializeAndDeserialize(ScheduleStrategy strategy, String typeName) throws Exception {
        String output = BridgeObjectMapper.get().writeValueAsString(strategy);
        JsonNode node = BridgeObjectMapper.get().readTree(output);
        assertEquals(typeName, node.get("type").asText());
        return BridgeObjectMapper.get().readValue(output, ScheduleStrategy.class);
    }

}
