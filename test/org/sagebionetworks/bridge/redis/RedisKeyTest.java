package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

public class RedisKeyTest {

    private BridgeConfig conf = BridgeConfigFactory.getConfig();
    private String devName = conf.getUser() + "-" + conf.getEnvironment().name().toLowerCase();
    
    @Test
    public void testSimpleKey() {
        assertEquals("lock", RedisKey.LOCK.getSuffix());
        assertEquals(devName + ":123:lock", RedisKey.LOCK.getRedisKey("123"));
        assertEquals("123", RedisKey.LOCK.getOriginalKey("123:lock"));
    }

    @Test
    public void testCompoundKey() {
        assertEquals("lock:health-code", RedisKey.HEALTH_CODE_LOCK.getSuffix());
        assertEquals(devName + ":123:lock:health-code",
                RedisKey.HEALTH_CODE_LOCK.getRedisKey("123"));
        assertEquals("123",
                RedisKey.HEALTH_CODE_LOCK
                        .getOriginalKey("123:lock:health-code"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidate() {
        assertEquals("123:lock", RedisKey.LOCK.getRedisKey("1:2:3"));
    }
}
