package org.sagebionetworks.bridge.dynamodb;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoAppConfigElementTest {
    
    @Test
    public void hashCodeEquals() {
        JsonNode clientData2 = TestUtils.getClientData();
        ((ObjectNode)clientData2).put("test", "tones");
        EqualsVerifier.forClass(DynamoAppConfigElement.class)
                .withPrefabValues(JsonNode.class, TestUtils.getClientData(), clientData2)
                .suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
}
