package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserProfile;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class UserProfileTest {

    @Test
    public void attributesSerializedCorrrectly() throws Exception {
        UserProfile profile = new UserProfile();
        profile.setAttribute("foo", "bar");
        
        String json = BridgeObjectMapper.get().writeValueAsString(profile);
        assertEquals("{\"foo\":\"bar\",\"type\":\"UserProfile\"}", json);
        
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        profile = UserProfile.fromJson(Sets.newHashSet("foo"), node);
        assertEquals("bar", profile.getAttribute("foo"));
    }
    
}
