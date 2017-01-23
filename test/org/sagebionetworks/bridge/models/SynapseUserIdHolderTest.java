package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.junit.Assert.assertEquals;

public class SynapseUserIdHolderTest {
    private static final String TEST_USER_ID = "test-id";

    @Test
    public void canSerialize() throws Exception {
        SynapseUserIdHolder holder = new SynapseUserIdHolder(TEST_USER_ID);

        String json = BridgeObjectMapper.get().writeValueAsString(holder);
        JsonNode node = BridgeObjectMapper.get().readTree(json);

        assertEquals(TEST_USER_ID, node.get("userId").asText());
        assertEquals("SynapseUserIdHolder", node.get("type").asText());
    }
}
