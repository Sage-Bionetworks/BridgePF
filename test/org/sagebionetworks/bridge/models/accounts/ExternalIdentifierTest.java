package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ExternalIdentifierTest {

    @Test
    public void canSerialize() throws Exception {
        String json = TestUtils.createJson("{'externalId':'AAA'}");
        
        ExternalIdentifier identifier = BridgeObjectMapper.get().readValue(json, ExternalIdentifier.class);
        assertEquals("AAA", identifier.getExternalId());
    }
    
}
