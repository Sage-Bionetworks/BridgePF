package org.sagebionetworks.bridge.redis;

import java.util.UUID;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.UserLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class RedisUserLockDao implements UserLockDao {

    private JedisStringOps stringOps = new JedisStringOps();

    @Override
    public void createLock(String healthDataCode) {
        try {
            String redisKey = RedisKey.LOCK.getRedisKey(healthDataCode);

            String setResult = stringOps.setnx(redisKey, UUID.randomUUID().toString()).execute();
            if (setResult == null) {
                throw new BridgeServiceException("Lock already set.", HttpStatus.SC_CONFLICT);
            }
            String expResult = stringOps.expire(redisKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS).execute();
            if (expResult == null) {
                throw new BridgeServiceException("Lock expiration not set.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Throwable e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

    }

    @Override
    public void releaseLock(String healthDataCode) {
        try {
            String redisKey = RedisKey.LOCK.getRedisKey(healthDataCode);

            String delResult = stringOps.delete(redisKey).execute();
            if (delResult == null) {
                throw new BridgeServiceException("Lock not released.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Throwable e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean isLocked(String healthDataCode) {
        String redisKey = RedisKey.LOCK.getRedisKey(healthDataCode);
        
        String getResult = stringOps.get(redisKey).execute();
        return "OK".equals(getResult) ? true : false;
    }

}
