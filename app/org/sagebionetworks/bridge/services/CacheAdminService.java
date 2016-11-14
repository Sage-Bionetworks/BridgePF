package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Set;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import com.google.common.collect.Sets;

@Component
public class CacheAdminService {

    private final String SESSION_SUFFIX = RedisKey.SEPARATOR + RedisKey.SESSION.getSuffix();
    private final String USER_SESSION_SUFFIX = RedisKey.SEPARATOR + RedisKey.SESSION.getSuffix() + RedisKey.SEPARATOR + RedisKey.USER.getSuffix();
    private final String REQUEST_INFO_SUFFIX = RedisKey.SEPARATOR + RedisKey.REQUEST_INFO.getSuffix();
    
    private JedisPool jedisPool;
    
    @Autowired
    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * Returns all keys in the cache that are not user session keys.
     * @return
     */
    public Set<String> listItems() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> allKeys = jedis.keys("*");
            Set<String> set = Sets.newHashSet();
            for (String key : allKeys) {
                if (notASessionKey(key)) {
                    set.add(key);
                }
            }
            return set;
        }
    }

    /**
     * Delete an item by its key from the cache (cannot delete sessions).
     * @param cacheKey
     */
    public void removeItem(String cacheKey) {
        checkArgument(isNotBlank(cacheKey));
        Long removed = null;
        
        if (notASessionKey(cacheKey)) {
            try (Jedis jedis = jedisPool.getResource()) {
                removed = jedis.del(cacheKey);
            }
        }
        if (removed == null || removed == 0) {
            throw new BridgeServiceException("Item could not be removed from cache: does key '"+cacheKey+"' exist?"); 
        }
    }
    
    private boolean notASessionKey(String key) {
        return !(key.endsWith(SESSION_SUFFIX) || key.endsWith(USER_SESSION_SUFFIX) || key.endsWith(REQUEST_INFO_SUFFIX));
    }
}
