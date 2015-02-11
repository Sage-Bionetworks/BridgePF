package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisStringOps;

import com.google.common.collect.Maps;

public class CacheProviderTest {

    private CacheProvider cacheProvider;
    
    @Before
    public void before() {
        cacheProvider = new CacheProvider();
        cacheProvider.setStringOps(getSimpleStringOps());
    }
    
    @Test
    public void addAndRemoveViewFromCacheProvider() throws Exception {
        Study study = new DynamoStudy();
        study.setIdentifier("test");
        study.setName("This is a test study");
        String json = BridgeObjectMapper.get().writeValueAsString(study);
        assertTrue(json != null && json.length() > 0);
        
        String cacheKey = study.getIdentifier() + ":Study";
        
        cacheProvider.setString(cacheKey, json, BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        String cachedString = cacheProvider.getString(cacheKey);
        assertEquals(json, cachedString);
        
        // Remove something that's not the key
        cacheProvider.removeString(cacheKey+"2");
        cachedString = cacheProvider.getString(cacheKey);
        assertEquals(json, cachedString);
        
        cacheProvider.removeString(cacheKey);
        cachedString = cacheProvider.getString(cacheKey);
        assertNull(cachedString);
    }
    
    
    private JedisStringOps getSimpleStringOps() {
        return new JedisStringOps() {
            private Map<String,String> map = Maps.newHashMap();
            public Long expire(final String key, final int seconds) {
                return 1L;
            }
            public String setex(final String key, final int seconds, final String value) {
                map.put(key, value);
                return "OK";
            }
            public Long setnx(final String key, final String value) {
                map.put(key, value);
                return 1L;
            }
            public String get(final String key) {
                return map.get(key);
            }
            public Long delete(final String key) {
                map.remove(key);
                return 1L;
            }
        };   
    }    
}
