package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisStringOps;
import org.sagebionetworks.bridge.redis.RedisKey;

import com.google.common.base.Supplier;
import com.google.common.collect.Maps;

public class ViewCacheTest {
    
    private BridgeObjectMapper mapper;
    private Study study;
    
    @Before
    public void before() {
        mapper = BridgeObjectMapper.get();
        
        study = new DynamoStudy();
        study.setIdentifier("testStudy");
        study.setName("Test Study");
    }
    
    @Test
    public void nothingWasCached() throws Exception {
        String cacheKey = getCacheKey(Study.class, study.getIdentifier());
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getString(cacheKey)).thenReturn(null);
        
        ViewCache cache = new ViewCache();
        cache.setCacheProvider(provider);
        
        String json = cache.getView(Study.class, study.getIdentifier(), new Supplier<Study>() {
            @Override public Study get() {
                Study study = new DynamoStudy();
                study.setName("Test Study 2");
                return study;
            }
        });
        
        Study foundStudy = DynamoStudy.fromJson(mapper.readTree(json));
        assertEquals("Test Study 2", foundStudy.getName());
    }
    
    @Test
    public void nothingWasCachedAndThereIsAnException() {
        String cacheKey = getCacheKey(Study.class, study.getIdentifier());
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getString(cacheKey)).thenReturn(null);
        
        ViewCache cache = new ViewCache();
        cache.setCacheProvider(provider);
        
        // It doesn't get wrapped or transformed or anything
        try {
            cache.getView(Study.class, study.getIdentifier(), new Supplier<Study>() {
                @Override public Study get() {
                    throw new BridgeServiceException("There has been a problem retrieving the study");
                }
            });
        } catch(BridgeServiceException e) {
            assertEquals("There has been a problem retrieving the study", e.getMessage());
        }
    }
    
    @Test
    public void somethingIsCached() throws Exception {
        String cacheKey = getCacheKey(Study.class, study.getIdentifier());
        String originalStudyJson = mapper.writeValueAsString(study);
        
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getString(cacheKey)).thenReturn(originalStudyJson);
        
        ViewCache cache = new ViewCache();
        cache.setCacheProvider(provider);
        
        String json = cache.getView(Study.class, study.getIdentifier(), new Supplier<Study>() {
            @Override public Study get() {
                Study study = new DynamoStudy();
                study.setName("Test Study 2");
                return study;
            }
        });
        
        Study foundStudy = DynamoStudy.fromJson(mapper.readTree(json));
        assertEquals("Test Study", foundStudy.getName());
    }
    
    @Test
    public void removeFromCacheWorks() throws Exception {
        ViewCache cache = new ViewCache();
        
        final String originalStudyJson = mapper.writeValueAsString(study);
        final String cacheKey = getCacheKey(Study.class, study.getIdentifier());
        cache.setCacheProvider(getSimpleCacheProvider(cacheKey, originalStudyJson));
        
        cache.removeView(Study.class, study.getIdentifier());
        
        String json = cache.getView(Study.class, study.getIdentifier(), new Supplier<Study>() {
            @Override public Study get() {
                Study study = new DynamoStudy();
                study.setName("Test Study 2");
                return study;
            }
        });
        Study foundStudy = DynamoStudy.fromJson(mapper.readTree(json));
        assertEquals("Test Study 2", foundStudy.getName());
    }
    
    private CacheProvider getSimpleCacheProvider(final String cacheKey, final String originalStudyJson) {
        return new CacheProvider() {
            private Map<String,String> map = Maps.newHashMap();
            {
                map.put(cacheKey, originalStudyJson);
            }
            public String getString(String cacheKey) {
                return map.get(cacheKey);
            }
            public void setString(String cacheKey, String value, int ttlSeconds) {
                map.put(cacheKey, value);
            }
            public void removeString(String cacheKey) {
                map.remove(cacheKey);
            }
        };   
    }
    
    private String getCacheKey(Class<?> clazz, String id) {
        return RedisKey.VIEW.getRedisKey(id + ":" + clazz.getSimpleName());
    }
   
}
