package org.sagebionetworks.bridge.redis;

import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.UserLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class RedisUserLockDaoTest {
    private UserLockDao lock = new RedisUserLockDao();
    private String healthCode = "1234";
    private String uuid;
    
    @Before
    public void before() {
        if (lock.isLocked(healthCode)) {
            lock.releaseLock(healthCode, uuid);
        }
    }
    
    @After
    public void after() {
        if (lock.isLocked(healthCode)) {
            lock.releaseLock(healthCode, uuid);
        }
    }
    
    @Test (expected = BridgeServiceException.class)
    public void createLockPreventsNewLock() {
        uuid = lock.createLock(healthCode);
        lock.createLock(healthCode);
    }
    
    @Test (expected = BridgeServiceException.class)
    public void releaseLockWhenNoLockErrors() {
        lock.releaseLock(healthCode, uuid);        
        lock.releaseLock(healthCode, uuid);
    }
    
    @Test
    public void createAndReleaseLockSuccess() {
        uuid = lock.createLock(healthCode);
        lock.releaseLock(healthCode, uuid);
        assertFalse(lock.isLocked(healthCode));
    }
    
}
