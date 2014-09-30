package org.sagebionetworks.bridge.redis;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class RedisDistributedLockDao implements DistributedLockDao {
    
    private JedisStringOps stringOps = new JedisStringOps();
    
    private String createKey(Class<?> clazz, String identifier) {
        return String.format("lock:%s:%s", clazz.getCanonicalName(), identifier);
    }
    
    @Override
    public String createLock(Class<?> clazz, String identifier) {
        String key = createKey(clazz, identifier);
        String id = BridgeUtils.generateGuid();
        String setResult = stringOps.setnx(key, id).execute();
        if (setResult == null) {
            throw new BridgeServiceException("Lock already set.");
        }

        String expResult = stringOps.expire(key, BridgeConstants.BRIDGE_UPDATE_ATTEMPT_EXPIRE_IN_SECONDS).execute();
        if (expResult == null) {
            throw new BridgeServiceException("Lock expiration not set.");
        }
        return id;
    }

    @Override
    public void releaseLock(Class<?> clazz, String identifier, String lockId) {
        if (lockId != null) {
            String key = createKey(clazz, identifier);
            String getResult = stringOps.get(key).execute();
            if (getResult == null) {
                throw new BridgeServiceException("Either lock expired or lock was never set.");
            } else if (!getResult.equals(lockId)) {
                throw new BridgeServiceException("Must be lock owner to release lock.");
            }
            String delResult = stringOps.delete(key).execute();
            if (delResult == null) {
                throw new BridgeServiceException("Lock not released.");
            }
        }
    }

    @Override
    public boolean isLocked(Class<?> clazz, String identifier) {
        String key = createKey(clazz, identifier);

        // For safety, set expiration if expiration not set.
        // Alx: Does this happen?!?
        if (stringOps.ttl(key) == null) {
            stringOps.expire(key, BridgeConstants.BRIDGE_UPDATE_ATTEMPT_EXPIRE_IN_SECONDS);
        }

        String getResult = stringOps.get(key).execute();
        return (getResult == null) ? false : true;
    }

}
