package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SharingOptionTest {

    @Test
    public void sharingOptionUsesCorrectDefaults() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("scope", SharingScope.SPONSORS_AND_PARTNERS.name().toLowerCase());
        
        SharingOption option = SharingOption.fromJson(node, 1);
        assertEquals(SharingScope.NO_SHARING, option.getSharingScope());
        
        option = SharingOption.fromJson(node, 2);
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, option.getSharingScope());
        
        try {
            node = JsonNodeFactory.instance.objectNode();
            option = SharingOption.fromJson(node, 2);
            fail("Should have thrown an invalid entity exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("scope is required"));
        }
    }
    
    @Test
    public void sharingOptionFailsGracefully() {
        SharingOption option = SharingOption.fromJson(null, 1);
        assertEquals(SharingScope.NO_SHARING, option.getSharingScope());
        
        try {
            option = SharingOption.fromJson(null, 11);
            fail("Should have thrown an invalid entity exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("scope is required"));
        }        
    }
    
}
