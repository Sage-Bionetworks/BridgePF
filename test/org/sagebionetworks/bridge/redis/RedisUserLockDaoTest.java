package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.UserLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class RedisUserLockDaoTest {
    UserLockDao lock = new RedisUserLockDao();
    private String healthDataCode = "1234";
    
    @Before
    public void before() {
        if (lock.isLocked(healthDataCode)) {
            lock.releaseLock(healthDataCode);
        }
    }
    
    @After
    public void after() {
        if (lock.isLocked(healthDataCode)) {
            lock.releaseLock(healthDataCode);
        }
    }
    
    @Test (expected = BridgeServiceException.class)
    public void createLockPreventsNewLock() {
        lock.createLock(healthDataCode);
        lock.createLock(healthDataCode);
    }
    
    @Test (expected = BridgeServiceException.class)
    public void releaseLockWhenNoLockErrors() {
        lock.releaseLock(healthDataCode);        
        lock.releaseLock(healthDataCode);
    }
    
    @Test
    public void createAndReleaseLockSuccess() {
        lock.createLock(healthDataCode);
        lock.releaseLock(healthDataCode);
        assertFalse(lock.isLocked(healthDataCode));
    }
    
}
