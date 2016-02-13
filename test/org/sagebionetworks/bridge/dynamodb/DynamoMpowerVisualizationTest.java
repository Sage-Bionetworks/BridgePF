package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.visualization.MpowerVisualization;

public class DynamoMpowerVisualizationTest {
    @Test
    public void jsonSerialization() throws Exception {
        // Start with JSON. Test visualization will be strings, even though in production they'll be complex objects.
        String jsonText = "{\n" +
                "   \"date\":\"2016-02-08\",\n" +
                "   \"healthCode\":\"dummyHealthCode\",\n" +
                "   \"visualization\":\"test-viz\"\n" +
                "}";

        // convert to POJO
        MpowerVisualization viz = BridgeObjectMapper.get().readValue(jsonText, MpowerVisualization.class);
        assertEquals(LocalDate.parse("2016-02-08"), viz.getDate());
        assertEquals("dummyHealthCode", viz.getHealthCode());
        assertEquals("test-viz", viz.getVisualization().textValue());

        // convert back into JSON
        String convertedJsonText = BridgeObjectMapper.get().writeValueAsString(viz);

        // Convert into a JSON node so we can validate it
        JsonNode convertedJsonNode = BridgeObjectMapper.get().readTree(convertedJsonText);
        assertEquals(4, convertedJsonNode.size());
        assertEquals("2016-02-08", convertedJsonNode.get("date").textValue());
        assertEquals("dummyHealthCode", convertedJsonNode.get("healthCode").textValue());
        assertEquals("test-viz", convertedJsonNode.get("visualization").textValue());
        assertEquals("MpowerVisualization", convertedJsonNode.get("type").textValue());
    }

    // Helper method to make a valid mPower visualization, public in case other tests need it.
    public static MpowerVisualization makeValidMpowerVisualization() {
        MpowerVisualization viz = new DynamoMpowerVisualization();
        viz.setDate(LocalDate.parse("2016-02-08"));
        viz.setHealthCode("dummyHealthCode");
        viz.setVisualization(new TextNode("test-viz"));
        return viz;
    }
}
