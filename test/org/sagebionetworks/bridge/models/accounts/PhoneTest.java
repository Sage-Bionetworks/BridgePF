package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class PhoneTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(Phone.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        Phone phone = new Phone(TestConstants.PHONE.getNationalFormat(), TestConstants.PHONE.getRegionCode());
        assertEquals(TestConstants.PHONE.getNumber(), phone.getNumber());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(phone);
        assertEquals(TestConstants.PHONE.getNumber(), node.get("number").textValue());
        assertEquals(TestConstants.PHONE.getRegionCode(), node.get("regionCode").textValue());
        assertEquals(TestConstants.PHONE.getNationalFormat(), node.get("nationalFormat").textValue());
        assertEquals("Phone", node.get("type").textValue());
        assertEquals(4, node.size());
        
        Phone deser = BridgeObjectMapper.get().readValue(node.toString(), Phone.class);
        assertEquals(phone.getNumber(), deser.getNumber());
        assertEquals(phone.getRegionCode(), deser.getRegionCode());
        assertEquals(TestConstants.PHONE.getNationalFormat(), deser.getNationalFormat());
    }
    
    @Test
    public void testToString() {
        assertEquals("Phone [regionCode=US, number=9712486796]", TestConstants.PHONE.toString());
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
        phone.setNumber(TestConstants.PHONE.getNationalFormat());
        phone.setRegionCode("US");
        assertEquals(TestConstants.PHONE.getNumber(), phone.getNumber());
        assertEquals(TestConstants.PHONE.getNationalFormat(), phone.getNationalFormat());
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
