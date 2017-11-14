package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AppleAppLinkTest {
    
    final static ObjectMapper MAPPER = new ObjectMapper();
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AppleAppLink.class).allFieldsShouldBeUsed().verify();
    }
    @Test
    public void canSerialize() throws Exception {
        AppleAppLink link = new AppleAppLink("appId", ImmutableList.of("/appId/", "/appId/*"));
        
        // We serialize this without the "type" attribute because we're following a schema handed
        // to us by Apple.
        JsonNode node = MAPPER.valueToTree(link);
        assertEquals(2, node.size());
        assertEquals("appId", node.get("appID").textValue());
        ArrayNode array = (ArrayNode)node.get("paths");
        assertEquals(2, array.size());
        assertEquals("/appId/", array.get(0).textValue());
        assertEquals("/appId/*", array.get(1).textValue());
        
        AppleAppLink deser = MAPPER.readValue(node.toString(), AppleAppLink.class);
        assertEquals("appId", deser.getAppId());
        assertEquals("/appId/", deser.getPaths().get(0));
        assertEquals("/appId/*", deser.getPaths().get(1));
    }
}
