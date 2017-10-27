package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class PhoneTest {

    @Test
    public void canSerialize() throws Exception {
        Phone phone = new Phone("408.258.8569", "US");
        assertEquals("+14082588569", phone.getNumber());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(phone);
        assertEquals("+14082588569", node.get("number").textValue());
        assertEquals("US", node.get("regionCode").textValue());
        assertEquals("(408) 258-8569", node.get("nationalFormat").textValue());
        assertEquals("Phone", node.get("type").textValue());
        assertEquals(4, node.size());
        
        Phone deser = BridgeObjectMapper.get().readValue(node.toString(), Phone.class);
        assertEquals(phone.getNumber(), deser.getNumber());
        assertEquals(phone.getRegionCode(), deser.getRegionCode());
        assertEquals("(408) 258-8569", deser.getNationalFormat());
    }
    
    @Test
    public void verifyPhoneFormatting() {
        // Forbidden planet, game store in London, in a local phone format.
        // Note that it's GB, not UK (!)
        Phone phone = new Phone("020-7420-3666", "GB");
        assertEquals("+442074203666", phone.getNumber());
        assertEquals("020 7420 3666", phone.getNationalFormat());
        assertEquals("GB", phone.getRegionCode());
    }
    
    @Test
    public void hibernateConstructionPathWorks() {
        Phone phone = new Phone();
        phone.setNumber("408-258-8569");
        phone.setRegionCode("US");
        assertEquals("+14082588569", phone.getNumber());
        assertEquals("(408) 258-8569", phone.getNationalFormat());
    }
    
    @Test
    public void invalidPhoneIsPreserved() {
        Phone phone = new Phone("999-999-9999", "US");
        assertEquals("999-999-9999", phone.getNumber());
        assertEquals("US", phone.getRegionCode());
        assertEquals("999-999-9999", phone.getNationalFormat());
        assertFalse(Phone.isValid(phone));
    }

    @Test(expected = NullPointerException.class)
    public void phoneIsNull() {
        Phone.isValid(null);
    }

    @Test
    public void phoneIsValid() {
        assertTrue(Phone.isValid(new Phone("206.547.2600", "US")));
    }
    
    @Test
    public void phoneIsNotValidAsPhone() {
        assertFalse(Phone.isValid(new Phone("999-999-9999", "US")));
    }
    
    @Test
    public void phoneIsNotValidAsANumber() {
        assertFalse(Phone.isValid(new Phone("206-SPARKIES-DINER", "US")));
    }
    
    @Test
    public void phoneIsMissingRegionCode() {
        assertFalse(Phone.isValid(new Phone("206.547.2600", null)));
    }
    
    @Test
    public void phoneIsMissingNumber() {
        assertFalse(Phone.isValid(new Phone(null, "US")));
    }
    
}
