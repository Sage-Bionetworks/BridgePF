package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Set;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.redis.RedisKey;

import com.google.common.collect.Sets;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class CacheAdminService {

    private final String SUFFIX = RedisKey.SEPARATOR + RedisKey.SESSION.getSuffix(); 
    private JedisPool jedisPool;
    
    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * Returns all keys in the cache that are not user session keys.
     * @return
     */
    public Set<String> listItems() {
        Jedis jedis = jedisPool.getResource();
        
        Set<String> allKeys = jedis.keys("*");
        
        Set<String> set = Sets.newHashSet();
        for (String key : allKeys) {
            if (!key.endsWith(SUFFIX)) {
                set.add(key);
            }
        }
        return set;
    }
    
    /**
     * Delete an item by its key from the cache (cannot delete sessions).
     * @param cacheKey
     */
    public void removeItem(String cacheKey) {
        checkArgument(isNotBlank(cacheKey));
        Long removed = null;
        if (!cacheKey.endsWith(SUFFIX)) {
            Jedis jedis = jedisPool.getResource();
            removed = jedis.del(cacheKey);
        }
        if (removed == null || removed == 0) {
            throw new BridgeServiceException("Item could not be removed from cache: does key '"+cacheKey+"' exist?"); 
        };
    }

}
