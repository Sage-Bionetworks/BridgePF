package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedisKeyTest {

    @Test
    public void testSimpleKey() {
        assertEquals("lock", RedisKey.LOCK.getSuffix());
        assertTrue(RedisKey.LOCK.getRedisKey("123").startsWith("123:lock"));
        assertEquals("123", RedisKey.LOCK.getOriginalKey("123:lock:user"));
        assertEquals("123", RedisKey.LOCK.getOriginalKey("123:lock"));
    }

    @Test
    public void testCompoundKey() {
        assertEquals("lock:health-code", RedisKey.HEALTH_CODE_LOCK.getSuffix());
        assertTrue("123:lock:health-code", RedisKey.HEALTH_CODE_LOCK.getRedisKey("123").startsWith("123:lock:health-code"));
        assertEquals("123", RedisKey.HEALTH_CODE_LOCK.getOriginalKey("123:lock:health-code:user"));
        assertEquals("123", RedisKey.HEALTH_CODE_LOCK.getOriginalKey("123:lock:health-code"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidate() {
        assertEquals("0:1:2:3", new SimpleKey("1:2:3").getRedisKey("0"));
    }
}
