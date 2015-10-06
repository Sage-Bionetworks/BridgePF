package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoSchedulePlanTest {

    @Test
    public void canSerializeDynamoSchedulePlan() throws Exception {
        DateTime datetime = DateTime.now().withZone(DateTimeZone.UTC);
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Label");
        plan.setGuid("guid");
        plan.setMinAppVersion(2);
        plan.setMaxAppVersion(10);
        plan.setModifiedOn(datetime.getMillis());
        plan.setStudyKey("test-study");
        plan.setVersion(2L);
        
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
    }
    
}
