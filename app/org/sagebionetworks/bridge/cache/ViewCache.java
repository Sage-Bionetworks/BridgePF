package org.sagebionetworks.bridge.cache;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;

@Component
public class ViewCache {
    
    private static final Logger logger = LoggerFactory.getLogger(ViewCache.class);
    
    public class ViewCacheKey<T> {
        private final String key;
        public ViewCacheKey(String key) {
            this.key = key;
        }
        String getKey() {
            return key;
        }
    };
    
    private CacheProvider cache;
    
    @Autowired
    public void setCacheProvider(CacheProvider cacheProvider) {
        this.cache = cacheProvider;
    }
    
    /**
     * Get the JSON for the viewCacheKey, or if nothing has been cached, call the supplier, 
     * cache the JSON representation of the object returned, and return that JSON.
     * @param key
     * @param supplier
     * @return
     */
    public <T> String getView(ViewCacheKey<T> key, Supplier<T> supplier) {
        try {
            String value = cache.getString(key.getKey());
            if (value == null) {
                value = cacheView(key, supplier);
            } else {
                //logger.debug("Retrieving " +key.getKey()+"' JSON from cache");
            }
            return value;
        } catch(JsonProcessingException e) {
            throw new BridgeServiceException(e);
        }
    }

    /**
     * Remove the JSON for the view represented by the viewCacheKey.
     * @param key
     */
    public <T> void removeView(ViewCacheKey<T> key) {
        //logger.debug("Deleting JSON for '" +key.getKey() +"'");
        cache.removeString(key.getKey());
    }
    
    /**
     * Create a viewCacheKey for a particular type of entity, and the set of identifiers 
     * that will identify that entity.
     * @param clazz
     * @param identifiers
     * @return
     */
    public <T> ViewCacheKey<T> getCacheKey(Class<T> clazz, String... identifiers) {
        String id = Joiner.on(":").join(identifiers);
        return new ViewCacheKey<T>(RedisKey.VIEW.getRedisKey(id + ":" + clazz.getName()));
    }
    
    private <T> String cacheView(ViewCacheKey<T> key, Supplier<T> supplier) throws JsonProcessingException {
        //logger.debug("Caching JSON for " +key.getKey()+"'");
        T object = supplier.get();
        String value = BridgeObjectMapper.get().writeValueAsString(object);
        cache.setString(key.getKey(), value);
        return value;
    }
    
}
