package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class PhoneTest {

    @Test
    public void canSerialize() throws Exception {
        Phone phone = new Phone("(408) 258-8569", "US");
        assertEquals("+14082588569", phone.getCanonicalPhone());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(phone);
        assertEquals("(408) 258-8569", node.get("number").textValue());
        assertEquals("US", node.get("regionCode").textValue());
        assertEquals("Phone", node.get("type").textValue());
        assertEquals(3, node.size());
        
        Phone deser = BridgeObjectMapper.get().readValue(node.toString(), Phone.class);
        assertEquals(phone.getNumber(), deser.getNumber());
        assertEquals(phone.getRegionCode(), deser.getRegionCode());
        assertEquals("+14082588569", deser.getCanonicalPhone());
    }
    
    @Test
    public void badPhoneReturnsNullCanonicalPhone() {
        Phone phone = new Phone("(408) 258-8569", null);
        assertNull(phone.getCanonicalPhone());
        
        phone = new Phone("(881) 258-8569", "FR");
        assertNull(phone.getCanonicalPhone());
        
        phone = new Phone(null, null);
        assertNull(phone.getCanonicalPhone());
        
        phone = new Phone("totally junk", "totally junk");
        assertNull(phone.getCanonicalPhone());
    }
    
}
