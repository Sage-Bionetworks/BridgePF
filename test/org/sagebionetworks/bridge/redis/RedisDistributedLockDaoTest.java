package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

public class RedisDistributedLockDaoTest {

    private JedisStringOps strOps;
    private DistributedLockDao lockDao;
    private String id;

    @Before
    public void before() {
        strOps = new JedisStringOps();
        lockDao = new RedisDistributedLockDao();
        id = UUID.randomUUID().toString();
    }

    @After
    public void after() {
        if (strOps != null) {
            strOps.clearRedis(id + "*");
        }
    }

    @Test
    public void test() {
        // Acquire lock
        String lockId = lockDao.acquire(getClass(), id, 60);
        String redisKey = RedisKey.LOCK.getRedisKey(
                id + RedisKey.SEPARATOR + getClass().getCanonicalName());
        String redisLockId = strOps.get(redisKey).execute();
        assertNotNull(redisLockId);
        assertEquals(redisLockId, lockId);
        assertTrue(strOps.ttl(redisKey).execute() > 0);
        // Acquire again should fail
        try {
            lockDao.acquire(getClass(), id);
        } catch (ConcurrentModificationException e) {
            assertTrue("ConcurrentModificationException expected", true);
        } catch (Throwable e) {
            fail(e.getMessage());
        }
        // Release lock
        boolean released = lockDao.release(getClass(), id, "incorrect lock id");
        assertFalse(released);
        released = lockDao.release(getClass(), id, lockId);
        assertTrue(released);
        // Once released, can be acquired
        lockId = lockDao.acquire(getClass(), id, 1);
        lockDao.release(getClass(), id, lockId);
    }
}
