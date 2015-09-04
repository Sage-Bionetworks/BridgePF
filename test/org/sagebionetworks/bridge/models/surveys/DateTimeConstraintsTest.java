package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class DateTimeConstraintsTest {

    @Test
    public void canSerializeCorrectly() throws Exception {
        DateTimeConstraints constraints = new DateTimeConstraints();
        constraints.setEarliestValue(DateTime.parse("2015-01-01T10:10:10-07:00"));
        constraints.setLatestValue(DateTime.parse("2015-12-31T10:10:10-07:00"));
        
        String json = BridgeObjectMapper.get().writeValueAsString(constraints);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        // The serialization is losing the time zone used by the user initially,
        // and this we don't want. We would need to create custom serializer/deserializers
        // for this.
        assertEquals("2015-01-01T10:10:10.000-07:00", node.get("earliestValue").asText());
        assertEquals("2015-12-31T10:10:10.000-07:00", node.get("latestValue").asText());
        assertEquals("datetime", node.get("dataType").asText());
        assertEquals("DateTimeConstraints", node.get("type").asText());
    }
    
}
