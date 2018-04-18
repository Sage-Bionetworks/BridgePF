package org.sagebionetworks.bridge.redis;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.lock.LockNotAvailableException;
import org.springframework.stereotype.Component;

@Component
public class RedisDistributedLockDao implements DistributedLockDao {

    private static final int EXPIRATION_IN_SECONDS = 3 * 60;
    
    private RedisLock redisLock;
    
    @Resource(name = "jedisOps")
    final void setJedisOps(JedisOps jedisOps) {
        redisLock = new RedisLock(jedisOps);
    }

    @Override
    public String acquireLock(final Class<?> clazz, final String identifier) {
        return acquireLock(clazz, identifier, EXPIRATION_IN_SECONDS);
    }

    @Override
    public String acquireLock(final Class<?> clazz, final String identifier, final int expireInSeconds) {
        checkNotNull(clazz);
        checkNotNull(identifier);
        checkArgument(expireInSeconds > 0);
        try {
            final CacheKey redisKey = CacheKey.lock(identifier, clazz);
            return redisLock.acquireLock(redisKey.toString(), expireInSeconds);
        } catch (LockNotAvailableException e) {
            throw new ConcurrentModificationException("Lock already set.");
        }
    }

    @Override
    public boolean releaseLock(final Class<?> clazz, final String identifier, final String lock) {
        checkNotNull(clazz);
        checkNotNull(identifier);
        checkNotNull(lock);
        try {
            final CacheKey redisKey = CacheKey.lock(identifier, clazz);
            return redisLock.releaseLock(redisKey.toString(), lock);
        } catch (RedisException e) {
            throw new BridgeServiceException("Lock not released.");
        }
    }
}
