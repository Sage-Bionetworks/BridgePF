package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.services.EmailVerificationStatus;

import com.fasterxml.jackson.databind.JsonNode;

public class EmailVerificationStatusHolderTest {
    
    @Test
    public void serializesCorrectly() throws Exception {
        EmailVerificationStatusHolder holder = new EmailVerificationStatusHolder(EmailVerificationStatus.PENDING);
        
        String json = BridgeObjectMapper.get().writeValueAsString(holder);
        System.out.println(json);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("EmailVerificationStatus", node.get("type").asText());
        assertEquals("pending", node.get("status").asText());
        
        EmailVerificationStatusHolder newHolder = BridgeObjectMapper.get().readValue(json, EmailVerificationStatusHolder.class);
        assertEquals(holder.getStatus(), newHolder.getStatus());
    }
}
