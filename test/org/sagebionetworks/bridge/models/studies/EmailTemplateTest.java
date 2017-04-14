package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;
import nl.jqno.equalsverifier.EqualsVerifier;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EmailTemplateTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(EmailTemplate.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void deserializeFromDynamoDB() throws Exception {
        // Original serialization used Jackson's default enum serialization, which BridgePF deserializes to correct 
        // MimeType enum. However client API code does not. Verify this older serialization continues to be deserialized
        // correctly after fixing serialization for rest/SDK code.
        String json = "{\"subject\":\"${studyName} sign in link\",\"body\":\"<p>${host}/${token}</p>\",\"mimeType\":\"HTML\"}";
        
        EmailTemplate template = new ObjectMapper().readValue(json, EmailTemplate.class);
        assertEquals(MimeType.HTML, template.getMimeType());
    }
    
    @Test
    public void canSerialize() throws Exception {
        EmailTemplate template = new EmailTemplate("Subject", "Body", MimeType.TEXT);
        
        String json = BridgeObjectMapper.get().writeValueAsString(template);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("Subject", node.get("subject").asText());
        assertEquals("Body", node.get("body").asText());
        assertEquals("EmailTemplate", node.get("type").asText());
        
        EmailTemplate template2 = BridgeObjectMapper.get().readValue(json, EmailTemplate.class);
        assertEquals(template, template2);
    }

}
