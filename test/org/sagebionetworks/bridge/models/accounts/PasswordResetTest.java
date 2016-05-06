package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class PasswordResetTest {

    @Test
    public void canDeserialize() throws Exception {
        String json = TestUtils.createJson("{'sptoken': '3x9HSBY3vZr5zrx9qkEtLa', 'password': 'pass', 'study': 'api'}");
        
        PasswordReset reset = BridgeObjectMapper.get().readValue(json, PasswordReset.class);
        assertEquals("3x9HSBY3vZr5zrx9qkEtLa", reset.getSptoken());
        assertEquals("pass", reset.getPassword());
        assertEquals("api", reset.getStudyIdentifier());
    }
    
}
