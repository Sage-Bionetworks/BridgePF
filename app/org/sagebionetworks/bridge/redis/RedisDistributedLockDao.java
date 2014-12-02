package org.sagebionetworks.bridge.redis;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

public class RedisDistributedLockDao implements DistributedLockDao {

    private static final int EXPIRATION_IN_SECONDS = 3 * 60;
    private final StringOps stringOps = new JedisStringOps();

    @Override
    public String acquire(final Class<?> clazz, final String identifier) {
        return acquire(clazz, identifier, EXPIRATION_IN_SECONDS);
    }

    @Override
    public String acquire(final Class<?> clazz, final String identifier, final int expireInSeconds) {
        checkNotNull(clazz);
        checkNotNull(identifier);
        checkArgument(expireInSeconds > 0);
        checkNotNull(clazz);
        checkNotNull(identifier);
        final String redisKey = createRedisKey(clazz, identifier);
        final String lockId = BridgeUtils.generateGuid();
        final Long result = stringOps.setnx(redisKey, lockId).execute();
        if (result != 1L) {
            Long expire = stringOps.ttl(redisKey).execute();
            if (expire < 0L) {
                expire(redisKey, expireInSeconds);
            }
            throw new ConcurrentModificationException("Lock already set.");
        }
        expire(redisKey, expireInSeconds);
        return lockId;
    }

    @Override
    public void release(Class<?> clazz, String identifier, String lockId) {
        checkNotNull(clazz);
        checkNotNull(identifier);
        checkNotNull(lockId);
        final String redisKey = createRedisKey(clazz, identifier);
        final String redisLockId = stringOps.get(redisKey).execute();
        if (!lockId.equals(redisLockId)) {
            return;
        }
        Long result = stringOps.delete(redisKey).execute();
        if (result != 1L) {
            throw new BridgeServiceException("Lock not released.");
        }
    }

    private String createRedisKey(Class<?> clazz, String identifier) {
        String key = identifier + RedisKey.SEPARATOR + clazz.getCanonicalName();
        return RedisKey.SESSION.getRedisKey(key);
    }

    private void expire(final String redisKey, final int expireInSeconds) {
        Long result = stringOps.expire(redisKey, expireInSeconds).execute();
        if (result != 1L) {
            // Try to recover by deleting the key
            stringOps.delete(redisKey).execute();
            throw new BridgeServiceException("Lock expiration not set.");
        }
    }
}
