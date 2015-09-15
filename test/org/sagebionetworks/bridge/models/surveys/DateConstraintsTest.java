package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class DateConstraintsTest {

    @Test
    public void canSerializeCorrectly() throws Exception {
        DateConstraints constraints = new DateConstraints();
        constraints.setEarliestValue(LocalDate.parse("2015-01-01"));
        constraints.setLatestValue(LocalDate.parse("2015-12-31"));
        
        String json = BridgeObjectMapper.get().writeValueAsString(constraints);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("2015-01-01", node.get("earliestValue").asText());
        assertEquals("2015-12-31", node.get("latestValue").asText());
        assertEquals("date", node.get("dataType").asText());
        assertEquals("DateConstraints", node.get("type").asText());
    }
    
    
}
