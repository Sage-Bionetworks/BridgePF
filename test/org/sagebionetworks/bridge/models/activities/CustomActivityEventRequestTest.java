package org.sagebionetworks.bridge.models.activities;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class CustomActivityEventRequestTest {
    private static final String EVENT_KEY = "my-event";
    private static final String EVENT_TIMESTAMP_STRING = "2018-04-04T16:43:11.357-07:00";
    private static final DateTime EVENT_TIMESTAMP = DateTime.parse(EVENT_TIMESTAMP_STRING);
    private static final String EVENT_ANSWER_VALUE = "oneValue";

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(CustomActivityEventRequest.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void builder() {
        CustomActivityEventRequest req = new CustomActivityEventRequest.Builder().withEventKey(EVENT_KEY)
                .withTimestamp(EVENT_TIMESTAMP).build();
        assertEquals(EVENT_KEY, req.getEventKey());
        TestUtils.assertDatesWithTimeZoneEqual(EVENT_TIMESTAMP, req.getTimestamp());
    }

    @Test
    public void serialize() throws Exception {
        // Start with JSON
        String jsonText = "{\n" +
                "   \"eventKey\":\"" + EVENT_KEY + "\",\n" +
                "   \"timestamp\":\"" + EVENT_TIMESTAMP_STRING + "\",\n" +
                "   \"answerValue\":\"" + EVENT_ANSWER_VALUE + "\"\n" +
                "}";

        // Convert to POJO
        CustomActivityEventRequest req = BridgeObjectMapper.get().readValue(jsonText,
                CustomActivityEventRequest.class);
        assertEquals(EVENT_KEY, req.getEventKey());
        TestUtils.assertDatesWithTimeZoneEqual(EVENT_TIMESTAMP, req.getTimestamp());

        // Convert back to JSON
        JsonNode node = BridgeObjectMapper.get().convertValue(req, JsonNode.class);
        assertEquals(4, node.size());
        assertEquals(EVENT_KEY, node.get("eventKey").textValue());
        assertEquals(EVENT_ANSWER_VALUE, node.get("answerValue").textValue());
        TestUtils.assertDatesWithTimeZoneEqual(EVENT_TIMESTAMP, DateTime.parse(node.get("timestamp").textValue()));
    }
}
