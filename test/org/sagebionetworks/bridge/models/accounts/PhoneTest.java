package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class PhoneTest {

    @Test
    public void canSerialize() throws Exception {
        Phone phone = new Phone("408.258.8569", "US");
        assertEquals("(408) 258-8569", phone.getNationalFormat());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(phone);
        assertEquals("+14082588569", node.get("number").textValue());
        assertEquals("US", node.get("regionCode").textValue());
        assertEquals("Phone", node.get("type").textValue());
        assertEquals(4, node.size());
        
        Phone deser = BridgeObjectMapper.get().readValue(node.toString(), Phone.class);
        assertEquals(phone.getNumber(), deser.getNumber());
        assertEquals(phone.getRegionCode(), deser.getRegionCode());
        assertEquals("(408) 258-8569", deser.getNationalFormat());
    }
    
    @Test
    public void hibernateConstructionPathWorks() {
        Phone phone = new Phone();
        phone.setNumber("408-258-8569");
        phone.setRegionCode("US");
        assertEquals("+14082588569", phone.getNumber());
        assertEquals("(408) 258-8569", phone.getNationalFormat());
    }
    
}
