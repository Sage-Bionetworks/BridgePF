package org.sagebionetworks.bridge.models.notifications;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class NotificationMessageTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(NotificationMessage.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        String json = TestUtils.createJson("{'subject':'The Subject','message':'The Message'}");

        NotificationMessage message = BridgeObjectMapper.get().readValue(json, NotificationMessage.class);
        assertEquals("The Subject", message.getSubject());
        assertEquals("The Message", message.getMessage());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(message);
        assertEquals("The Subject", node.get("subject").asText());
        assertEquals("The Message", node.get("message").asText());
        assertEquals("NotificationMessage", node.get("type").asText());
    }
}
