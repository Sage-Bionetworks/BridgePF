package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class JedisStringOpsTest {
    
    @Resource
    private JedisStringOps stringOps;
    
    @Test
    public void setsAndReadsValueFromRedis() throws Exception {
        assertEquals("OK", stringOps.setex("testKey", 2, "testValue"));
        assertEquals("testValue", stringOps.get("testKey"));
        Thread.sleep(3000);
        assertNull(stringOps.get("testKey"));
    }
}
