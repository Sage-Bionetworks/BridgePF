package org.sagebionetworks.bridge.models.sms;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SmsTypeTest {
    @Test
    public void testGetValue() {
        assertEquals("Promotional", SmsType.PROMOTIONAL.getValue());
        assertEquals("Transactional", SmsType.TRANSACTIONAL.getValue());
    }
}
