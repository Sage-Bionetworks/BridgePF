package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.UserLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class RedisUserLockDaoTest {
    private UserLockDao lock = new RedisUserLockDao();
    private String healthDataCode = "1234";
    private String uuid;
    
    @Before
    public void before() {
        if (lock.isLocked(healthDataCode)) {
            lock.releaseLock(healthDataCode, uuid);
        }
    }
    
    @After
    public void after() {
        if (lock.isLocked(healthDataCode)) {
            lock.releaseLock(healthDataCode, uuid);
        }
    }
    
    @Test (expected = BridgeServiceException.class)
    public void createLockPreventsNewLock() {
        uuid = lock.createLock(healthDataCode);
        lock.createLock(healthDataCode);
    }
    
    @Test (expected = BridgeServiceException.class)
    public void releaseLockWhenNoLockErrors() {
        lock.releaseLock(healthDataCode, uuid);        
        lock.releaseLock(healthDataCode, uuid);
    }
    
    @Test
    public void createAndReleaseLockSuccess() {
        uuid = lock.createLock(healthDataCode);
        lock.releaseLock(healthDataCode, uuid);
        assertFalse(lock.isLocked(healthDataCode));
    }
    
}
