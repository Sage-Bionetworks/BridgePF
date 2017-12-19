package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class OAuthProviderTest {
    
    public static final String CALLBACK_URL = "https://docs.sagebridge.org/crf-module/";

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(OAuthProvider.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", CALLBACK_URL);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(provider);
        assertEquals("clientId", node.get("clientId").textValue());
        assertEquals("secret", node.get("secret").textValue());
        assertEquals("endpoint", node.get("endpoint").textValue());
        assertEquals(CALLBACK_URL, node.get("callbackUrl").textValue());
        assertEquals("OAuthProvider", node.get("type").textValue());
        assertEquals(5, node.size());
        
        OAuthProvider deser = BridgeObjectMapper.get().readValue(node.toString(), OAuthProvider.class);
        assertEquals("clientId", deser.getClientId());
        assertEquals("secret", deser.getSecret());
        assertEquals("endpoint", deser.getEndpoint());
        assertEquals(CALLBACK_URL, deser.getCallbackUrl());
    }
}
