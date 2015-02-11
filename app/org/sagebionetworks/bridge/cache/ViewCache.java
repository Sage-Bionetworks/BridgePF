package org.sagebionetworks.bridge.cache;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Supplier;

public class ViewCache {
    
    // Could add a BlockingQueue and Executors.newSingleThreadExecutor() to prevent a run of 
    // requests when the view is expired. But I don't think this is likely to be a very big
    // problem.

    private static Logger logger = LoggerFactory.getLogger(ViewCache.class);
    
    private CacheProvider cache;
    
    public void setCacheProvider(CacheProvider cacheProvider) {
        this.cache = cacheProvider;
    }
    
    public <T> String getView(Class<T> clazz, String id, Supplier<T> supplier) {
        try {
            String cacheKey = getCacheKey(clazz, id);
            String value = cache.getString(cacheKey);
            if (value == null) {
                value = cacheView(cacheKey, supplier);
            } else {
                logger.debug("Retrieving " +cacheKey+"' JSON from cache");
            }
            return value;
        } catch(JsonProcessingException e) {
            throw new BridgeServiceException(e);
        }
    }

    public void removeView(Class<?> clazz, String id) {
        logger.debug("Deleting JSON for " +clazz.getSimpleName()+" '"+id+"'");
        String cacheKey = getCacheKey(clazz, id);
        cache.removeString(cacheKey);
    }

    private <T> String cacheView(String cacheKey, Supplier<T> supplier) throws JsonProcessingException {
        logger.debug("Caching JSON for " +cacheKey+"'");
        T object = supplier.get();
        String value = BridgeObjectMapper.get().writeValueAsString(object);
        cache.setString(cacheKey, value, 18000); // 5 hours
        return value;
    }
    
    private String getCacheKey(Class<?> clazz, String id) {
        return RedisKey.VIEW.getRedisKey(id + ":" + clazz.getSimpleName());
    }
    
}
