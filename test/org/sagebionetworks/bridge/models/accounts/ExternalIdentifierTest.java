package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class ExternalIdentifierTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoExternalIdentifier.class).allFieldsShouldBeUsed()
                .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        String json = TestUtils.createJson("{'identifier':'AAA','substudyId':'sub-study-id'}");
        
        ExternalIdentifier identifier = BridgeObjectMapper.get().readValue(json, ExternalIdentifier.class);
        assertEquals("AAA", identifier.getIdentifier());
        assertEquals("sub-study-id", identifier.getSubstudyId());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(identifier);
        
        assertEquals(3, node.size());
        assertEquals("AAA", node.get("identifier").textValue());
        assertEquals("sub-study-id", node.get("substudyId").textValue());
        assertEquals("ExternalIdentifier", node.get("type").textValue());
    }
    
}
