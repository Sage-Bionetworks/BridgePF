package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

// TODO: Spring-ify or re-write this.
public class JedisStringOpsTest {
    @Test
    public void setsAndReadsValueFromRedis() throws Exception {
        JedisStringOps strOps = new JedisStringOps();
        assertEquals("OK", strOps.setex("testKey", 2, "testValue"));
        assertEquals("testValue", strOps.get("testKey"));
        Thread.sleep(3000);
        assertNull(strOps.get("testKey"));
    }
}
