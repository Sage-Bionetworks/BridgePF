package org.sagebionetworks.bridge.models;


import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;

import static org.junit.Assert.assertEquals;

public class SynapseAccountTest {
    private static final String TEST_LAST_NAME = "last-name";
    private static final String TEST_FIRST_NAME = "first-name";
    private static final String TEST_EMAIL = "test-email@test.com";
    private static final String TEST_USER_NAME = "test-user-name";

    @Test
    public void canSerialize() throws Exception {
        SynapseAccount synapseAccount = new SynapseAccount();
        synapseAccount.setLastName(TEST_LAST_NAME);
        synapseAccount.setFirstName(TEST_FIRST_NAME);
        synapseAccount.setUsername(TEST_USER_NAME);
        synapseAccount.setEmail(TEST_EMAIL);

        String json = BridgeObjectMapper.get().writeValueAsString(synapseAccount);
        JsonNode node = BridgeObjectMapper.get().readTree(json);

        assertEquals(TEST_LAST_NAME, node.get("lastName").asText());
        assertEquals(TEST_FIRST_NAME, node.get("firstName").asText());
        assertEquals(TEST_EMAIL, node.get("email").asText());
        assertEquals(TEST_USER_NAME, node.get("username").asText());
    }
}
