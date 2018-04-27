package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GeneratedPasswordTest {
    @Test
    public void test() { 
        GeneratedPassword gp = new GeneratedPassword("externalId", "userId", "password");
        assertEquals("externalId", gp.getExternalId());
        assertEquals("userId", gp.getUserId());
        assertEquals("password", gp.getPassword());
    }
}
