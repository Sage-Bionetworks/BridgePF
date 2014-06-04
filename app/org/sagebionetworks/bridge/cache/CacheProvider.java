package org.sagebionetworks.bridge.cache;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * A wrapper around whatever cache provider we ultimately decide to go with (probably Redis). 
 * Assuming for the moment that we can store objects, by serialization if we have to.
 */
public class CacheProvider {

    Cache<String, Object> cache = CacheBuilder.newBuilder().expireAfterAccess(20, TimeUnit.MINUTES).build();
            
    public void set(String key, Object value) {
        cache.put(key, value);
    }
    
    public Object get(String key) {
        return cache.getIfPresent(key);
    }
    
    public void remove(String key) {
        cache.invalidate(key);
    }

}
