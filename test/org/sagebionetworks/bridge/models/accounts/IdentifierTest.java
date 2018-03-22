package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class IdentifierTest {

    @Test
    public void canDeserialize() throws Exception {
        String json = TestUtils.createJson("{'study':'api'," +
                "'email': '"+TestConstants.EMAIL+"'," + 
                "'phone': {'number': '"+TestConstants.PHONE.getNumber()+"', "+
                "'regionCode':'"+TestConstants.PHONE.getRegionCode()+"'}}");
        
        Identifier identifier = BridgeObjectMapper.get().readValue(json, Identifier.class);
        
        assertEquals(TestConstants.TEST_STUDY, identifier.getStudyIdentifier());
        assertEquals(TestConstants.EMAIL, identifier.getEmail());
        assertEquals(TestConstants.PHONE.getNumber(), identifier.getPhone().getNumber());
        assertEquals(TestConstants.PHONE.getRegionCode(), identifier.getPhone().getRegionCode());
    }
    
}
