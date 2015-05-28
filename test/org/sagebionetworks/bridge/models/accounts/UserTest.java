package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class UserTest {

    @Test
    public void testHealthCodeEncryption() throws IOException {
        User user = new User();
        user.setEmail("userEmail");
        user.setId("userId");
        user.setHealthCode("123abc");
        String userSer = BridgeObjectMapper.get().writeValueAsString(user);
        assertNotNull(userSer);
        assertFalse("Health code should have been encrypted in the serialized string.",
                userSer.toLowerCase().contains("123abc"));
        User userDe = BridgeObjectMapper.get().readValue(userSer, User.class);
        assertNotNull(userDe);
        assertEquals("123abc", userDe.getHealthCode());
    }
}
