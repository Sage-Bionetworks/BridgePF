package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.*;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class PasswordPolicyTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(PasswordPolicy.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        PasswordPolicy policy = new PasswordPolicy(8, true, false, true);
        
        String json = BridgeObjectMapper.get().writeValueAsString(policy);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(8, node.get("minLength").asInt());
        assertEquals(true, node.get("numericRequired").asBoolean());
        assertEquals(false, node.get("symbolRequired").asBoolean());
        assertEquals(true, node.get("upperCaseRequired").asBoolean());
        assertEquals("PasswordPolicy", node.get("type").asText());
        
        PasswordPolicy policy2 = BridgeObjectMapper.get().readValue(json, PasswordPolicy.class);
        assertEquals(policy, policy2);
    }
}
