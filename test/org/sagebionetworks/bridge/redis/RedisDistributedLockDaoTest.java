package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.UUID;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class RedisDistributedLockDaoTest {

    @Resource
    private JedisOps jedisOps;
    @Resource
    private DistributedLockDao lockDao;
    private String id;

    @Before
    public void before() {
        id = UUID.randomUUID().toString();
    }

    @After
    public void after() {
        if (jedisOps != null) {
            jedisOps.clearRedis(id + "*");
        }
    }

    @Test
    public void test() {
        // Acquire lock
        String lockId = lockDao.acquireLock(getClass(), id, 60);
        assertNotNull(lockId);
        String redisKey = RedisKey.LOCK.getRedisKey(
                id + RedisKey.SEPARATOR + getClass().getCanonicalName());
        String redisLockId = jedisOps.get(redisKey);
        assertNotNull(redisLockId);
        assertEquals(redisLockId, lockId);
        assertTrue(jedisOps.ttl(redisKey) > 0);
        // Acquire again should get back an exception
        try {
            assertNull(lockDao.acquireLock(getClass(), id));
        } catch (ConcurrentModificationException e) {
            assertTrue("ConcurrentModificationException expected.", true);
        } catch (Throwable e) {
            fail();
        }
        // Release lock
        boolean released = lockDao.releaseLock(getClass(), id, "incorrect lock id");
        assertFalse(released);
        released = lockDao.releaseLock(getClass(), id, lockId);
        assertTrue(released);
        // Once released, can be re-acquired
        lockId = lockDao.acquireLock(getClass(), id, 1);
        lockDao.releaseLock(getClass(), id, lockId);
    }
}
