package org.sagebionetworks.bridge.cache;

import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;

public class ViewCache {
    
    private static final Logger logger = LoggerFactory.getLogger(ViewCache.class);
    
    private CacheProvider cache;
    private ObjectMapper objectMapper;
    private int cachePeriod;
    
    public final void setCacheProvider(CacheProvider cacheProvider) {
        this.cache = cacheProvider;
    }
    
    public final void setObjectMapper(ObjectMapper mapper) {
        this.objectMapper = mapper;
    }
    
    public final void setCachePeriod(int cachePeriod) {
        this.cachePeriod = cachePeriod;
    }
    
    /**
     * Get the JSON for the viewCacheKey, or if nothing has been cached, call the supplier, 
     * cache the JSON representation of the object returned, and return that JSON.
     * @param key
     * @param supplier
     * @return
     */
    public <T> String getView(CacheKey key, Supplier<T> supplier) {
        try {
            String value = cache.getObject(key, String.class);
            if (value == null) {
                value = cacheView(key, supplier);
            } else {
                logger.debug("Retrieving "+key+"' JSON from cache");
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
    public <T> void removeView(CacheKey key) {
        logger.debug("Deleting JSON for '"+key+"'");
        cache.removeObject(key);
    }
    
    /**
     * Create a viewCacheKey for a particular type of entity, and the set of identifiers 
     * that will identify that entity.
     * @param clazz
     * @param identifiers
     * @return
     */
    public <T> CacheKey getCacheKey(Class<T> clazz, String... identifiers) {
        return CacheKey.viewKey(clazz, identifiers);
    }
    
    private <T> String cacheView(CacheKey key, Supplier<T> supplier) throws JsonProcessingException {
        logger.debug("Caching JSON for "+key+"'");
        T object = supplier.get();
        String value = objectMapper.writeValueAsString(object);
        cache.setObject(key, value, cachePeriod);
        return value;
    }
}
