package org.sagebionetworks.bridge.models.studies;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.junit.Assert.assertEquals;

public class SynapseProjectIdTeamIdHolderTest {
    private static final String TEST_PROJECT_ID = "test-project-id";
    private static final Long TEST_TEAM_ID = Long.parseLong("1234");

    @Test
    public void serializesCorrectly() throws Exception {
        SynapseProjectIdTeamIdHolder holder = new SynapseProjectIdTeamIdHolder(TEST_PROJECT_ID, TEST_TEAM_ID);

        String synapseIds = BridgeObjectMapper.get().writeValueAsString(holder);
        JsonNode synapse = BridgeObjectMapper.get().readTree(synapseIds);

        assertEquals(TEST_PROJECT_ID, synapse.get("projectId").asText());
        assertEquals(TEST_TEAM_ID.longValue(), synapse.get("teamId").asLong());
    }
}
