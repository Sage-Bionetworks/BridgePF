package org.sagebionetworks.bridge.cache;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;

public class ViewCache {
    
    // Could add a BlockingQueue and Executors.newSingleThreadExecutor() to prevent a run of 
    // requests when the view is expired. But I don't think this is likely to be a very big
    // problem.

    public class ViewCacheKey {
        private final String key;
        public ViewCacheKey(String key) {
            this.key = key;
        }
        String getKey() {
            return key;
        }
    };
    
    private static Logger logger = LoggerFactory.getLogger(ViewCache.class);
    
    private CacheProvider cache;
    
    public void setCacheProvider(CacheProvider cacheProvider) {
        this.cache = cacheProvider;
    }
    
    /**
     * Get the view cached with the given identifier, or if that view has not been cached, 
     * call the supplier, convert the response to JSON and cache it, then return that view.
     * @param clazz
     * @param id
     * @param supplier
     * @return
     */
    public <T> String getView(ViewCacheKey key, Supplier<T> supplier) {
        try {
            String value = cache.getString(key.getKey());
            if (value == null) {
                value = cacheView(key, supplier);
            } else {
                logger.debug("Retrieving " +key.getKey()+"' JSON from cache");
            }
            return value;
        } catch(JsonProcessingException e) {
            throw new BridgeServiceException(e);
        }
    }

    /**
     * Remove the view with the given identifier.
     * @param clazz
     * @param id
     */
    public void removeView(ViewCacheKey key) {
        logger.debug("Deleting JSON for '" +key.getKey() +"'");
        cache.removeString(key.getKey());
    }
    
    public ViewCacheKey getCacheKey(Class<?> clazz, String... identifiers) {
        String id = Joiner.on(":").join(identifiers);
        return new ViewCacheKey(RedisKey.VIEW.getRedisKey(id + ":" + clazz.getName()));
    }
    
    private <T> String cacheView(ViewCacheKey key, Supplier<T> supplier) throws JsonProcessingException {
        logger.debug("Caching JSON for " +key.getKey()+"'");
        T object = supplier.get();
        String value = BridgeObjectMapper.get().writeValueAsString(object);
        cache.setString(key.getKey(), value);
        return value;
    }
    
}
