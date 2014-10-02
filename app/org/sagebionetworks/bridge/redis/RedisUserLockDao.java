package org.sagebionetworks.bridge.redis;

import java.util.UUID;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.UserLockDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

public class RedisUserLockDao implements UserLockDao {

    private JedisStringOps stringOps = new JedisStringOps();

    @Override
    public String createLock(String stormpathID) {
        try {
            String redisKey = RedisKey.LOCK.getRedisKey(stormpathID);
            String uuid = UUID.randomUUID().toString();

            Long result = stringOps.setnx(redisKey, uuid).execute();
            if (result != 1L) {
                throw new ConcurrentModificationException("Lock already set.");
            }

            result = stringOps.expire(redisKey, BridgeConstants.BRIDGE_UPDATE_ATTEMPT_EXPIRE_IN_SECONDS).execute();
            if (result != 1L) {
                throw new BridgeServiceException("Lock expiration not set.");
            }

            return uuid;
        } catch (Throwable e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public void releaseLock(String stormpathID, String uuid) {
        try {
            String redisKey = RedisKey.LOCK.getRedisKey(stormpathID);

            String getResult = stringOps.get(redisKey).execute();
            if (getResult == null) {
                throw new BadRequestException("Either lock expired or lock was never set.");
            } else if (!getResult.equals(uuid)) {
                throw new BadRequestException("Must be lock owner to release lock.");
            }
            Long result = stringOps.delete(redisKey).execute();
            if (result == 0L) {
                throw new BridgeServiceException("Lock not released.");
            }
        } catch (Throwable e) {
            throw new BridgeServiceException(e);
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
