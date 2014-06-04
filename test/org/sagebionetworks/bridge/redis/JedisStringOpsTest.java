package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JedisStringOpsTest {
    @Test
    public void test() throws Exception {
        StringOps strOps = new JedisStringOps();
        assertEquals("OK", strOps.setex("testKey", 10, "testValue").execute());
        assertEquals("testValue", strOps.get("testKey").execute());
    }
}
