package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SmsTemplateTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SmsTemplate.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void deserializeFromDynamoDB() throws Exception {
        String json = "{\"message\":\"a message\"}";
        
        SmsTemplate template = new ObjectMapper().readValue(json, SmsTemplate.class);
        assertEquals("a message", template.getMessage());
    }
    
    @Test
    public void canSerialize() throws Exception {
        SmsTemplate template = new SmsTemplate("a message");
        
        String json = BridgeObjectMapper.get().writeValueAsString(template);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("a message", node.get("message").textValue());
        
        SmsTemplate template2 = BridgeObjectMapper.get().readValue(json, SmsTemplate.class);
        assertEquals(template, template2);
    }
}
