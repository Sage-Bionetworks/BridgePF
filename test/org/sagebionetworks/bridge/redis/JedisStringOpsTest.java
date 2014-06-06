package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class JedisStringOpsTest {
    @Test
    public void setsAndReadsValueFromRedis() throws Exception {
        StringOps strOps = new JedisStringOps();
        assertEquals("OK", strOps.setex("testKey", 2, "testValue").execute());
        assertEquals("testValue", strOps.get("testKey").execute());
        Thread.sleep(2000);
        assertNull(strOps.get("testKey").execute());
    }
}
