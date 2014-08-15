package org.sagebionetworks.bridge.redis;

import java.util.UUID;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.UserLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class RedisUserLockDao implements UserLockDao {

    private JedisStringOps stringOps = new JedisStringOps();

    @Override
    public String createLock(String stormpathID) {
        try {
            String redisKey = RedisKey.LOCK.getRedisKey(stormpathID);
            String uuid = UUID.randomUUID().toString();
            
            String setResult = stringOps.setnx(redisKey, uuid).execute();
            if (setResult == null) {
                throw new BridgeServiceException("Lock already set.", HttpStatus.SC_CONFLICT);
            }
            
            String expResult = stringOps.expire(redisKey, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS).execute();
            if (expResult == null) {
                throw new BridgeServiceException("Lock expiration not set.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
            
            return uuid;
        } catch (Throwable e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

    }

    @Override
    public void releaseLock(String stormpathID, String uuid) {
        try {
            String redisKey = RedisKey.LOCK.getRedisKey(stormpathID);

            String getResult = stringOps.get(redisKey).execute();
            if (getResult == null) {
                throw new BridgeServiceException("Either lock expired or lock was never set.", HttpStatus.SC_BAD_REQUEST);
            } else if (!getResult.equals(uuid)) {
                throw new BridgeServiceException("Must be lock owner to release lock.", HttpStatus.SC_BAD_REQUEST);
            }
            
            String delResult = stringOps.delete(redisKey).execute();
            if (delResult == null) {
                throw new BridgeServiceException("Lock not released.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Throwable e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean isLocked(String stormpathID) {
        String redisKey = RedisKey.LOCK.getRedisKey(stormpathID);
        
        // For safety, set expiration if expiration not set.
        if (stringOps.ttl(redisKey) == null) {
            stringOps.expire(redisKey, BridgeConstants.BRIDGE_UPDATE_ATTEMPT_EXPIRE_IN_SECONDS);
        }
        
        String getResult = stringOps.get(redisKey).execute();
        return (getResult == null) ? false : true;
    }

}
