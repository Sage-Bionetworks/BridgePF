package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class EmailSignInRequestTest {

    @Test
    public void deserializeEmailSignInRequest() throws Exception {
        String json = TestUtils.createJson("{'study':'foo','email':'email@email.com','token':'tokenValue'}");
        EmailSignInRequest request = BridgeObjectMapper.get().readValue(json, EmailSignInRequest.class);
        
        assertEquals("foo", request.getStudyId());
        assertEquals("email@email.com", request.getEmail());
        assertEquals("tokenValue", request.getToken());
    }
    
}
