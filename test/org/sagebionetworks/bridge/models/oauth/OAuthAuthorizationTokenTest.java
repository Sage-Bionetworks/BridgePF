package org.sagebionetworks.bridge.models.oauth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class OAuthAuthorizationTokenTest {
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(OAuthAuthorizationToken.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerialize() throws Exception {
        OAuthAuthorizationToken token = new OAuthAuthorizationToken("vendorId", "authToken");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(token);
        assertEquals("vendorId", node.get("vendorId").textValue());
        assertEquals("authToken", node.get("authToken").textValue());
        assertEquals("OAuthAuthorizationToken", node.get("type").textValue());
        assertEquals(3, node.size());
        
        OAuthAuthorizationToken deser = BridgeObjectMapper.get().readValue(node.toString(), OAuthAuthorizationToken.class);
        assertEquals("vendorId", deser.getVendorId());
        assertEquals("authToken", deser.getAuthToken());
        
        assertEquals(token, deser);
    }
}
