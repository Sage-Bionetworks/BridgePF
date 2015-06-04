package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class EmailTemplateTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(EmailTemplate.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        EmailTemplate template = new EmailTemplate("Subject", "Body");
        
        String json = BridgeObjectMapper.get().writeValueAsString(template);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("Subject", node.get("subject").asText());
        assertEquals("Body", node.get("body").asText());
        assertEquals("EmailTemplate", node.get("type").asText());
        
        EmailTemplate template2 = BridgeObjectMapper.get().readValue(json, EmailTemplate.class);
        assertEquals(template, template2);
    }

}
