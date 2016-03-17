package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ParticipantOptionsTest {

    @Test
    public void canDeserializeJson() throws Exception {
        String json = TestUtils.createJson("{'sharingScope':'sponsors_and_partners',"+
                "'notifyByEmail':true,'externalId':'abcd','dataGroups':['group1','group2'],"+
                "'languages':['en','fr']}");
        
        ParticipantOptions options = BridgeObjectMapper.get().readValue(json, ParticipantOptions.class);
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, options.getSharingScope());
        assertEquals(Boolean.TRUE, options.getNotifyByEmail());
        assertEquals("abcd", options.getExternalId());
        assertTrue(options.getDataGroups().contains("group1"));
        assertTrue(options.getDataGroups().contains("group2"));
        assertTrue(options.getLanguages().contains("en"));
        assertTrue(options.getLanguages().contains("fr"));
        assertFalse(options.hasNoUpdates());
    }
    
    @Test
    public void partialJsonCreatesNulls() throws Exception {
        String json = TestUtils.createJson("{'sharingScope':'sponsors_and_partners',"+
                "'externalId':'abcd','dataGroups':['group1','group2']}");
        
        ParticipantOptions options = BridgeObjectMapper.get().readValue(json, ParticipantOptions.class);
        assertNull(options.getNotifyByEmail());
        assertNull(options.getLanguages());
        assertFalse(options.hasNoUpdates());
    }
    
    @Test
    public void nullTest() {
        ParticipantOptions options = new ParticipantOptions(null, null, null, null, null);
        assertTrue(options.hasNoUpdates());
    }
}
