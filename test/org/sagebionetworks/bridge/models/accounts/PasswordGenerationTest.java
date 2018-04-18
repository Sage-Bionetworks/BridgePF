package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class PasswordGenerationTest {
    
    @Test
    public void canDeserialize() throws Exception {
        String json = TestUtils.createJson("{'externalId':'abc','createAccount':false}");
        
        PasswordGeneration passgen = BridgeObjectMapper.get().readValue(json, PasswordGeneration.class);
        assertEquals("abc", passgen.getExternalId());
        assertFalse(passgen.isCreateAccount());
    }
    
    @Test
    public void defaultsWork() throws Exception {
        String json = TestUtils.createJson("{'externalId':'abc'}");
        
        PasswordGeneration passgen = BridgeObjectMapper.get().readValue(json, PasswordGeneration.class);
        assertEquals("abc", passgen.getExternalId());
        assertTrue(passgen.isCreateAccount());
    }
    
}
