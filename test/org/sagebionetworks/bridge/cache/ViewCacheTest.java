package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.base.Supplier;
import com.google.common.collect.Maps;

public class ViewCacheTest {
    
    private BridgeObjectMapper mapper;
    private Study study;
    
    @Before
    public void before() {
        mapper = BridgeObjectMapper.get();
        
        study = TestUtils.getValidStudy();
    }
    
    @Test
    public void nothingWasCached() throws Exception {
        ViewCache cache = new ViewCache();
        ViewCacheKey<Study> cacheKey = cache.getCacheKey(Study.class, study.getIdentifier());
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getString(cacheKey.getKey())).thenReturn(null);
        cache.setCacheProvider(provider);
        
        String json = cache.getView(cacheKey, new Supplier<Study>() {
            @Override public Study get() {
                Study study = TestUtils.getValidStudy();
                study.setName("Test Study 2");
                return study;
            }
        });
        
        Study foundStudy = BridgeObjectMapper.get().readValue(json, DynamoStudy.class);
        assertEquals("Test Study 2", foundStudy.getName());
    }
    
    @Test
    public void nothingWasCachedAndThereIsAnException() {
        ViewCache cache = new ViewCache();
        ViewCacheKey<Study> cacheKey = cache.getCacheKey(Study.class, study.getIdentifier());
        
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getString(cacheKey.getKey())).thenReturn(null);
        cache.setCacheProvider(provider);
        
        // It doesn't get wrapped or transformed or anything
        try {
            cache.getView(cacheKey, new Supplier<Study>() {
                @Override public Study get() {
                    throw new BridgeServiceException("There has been a problem retrieving the study");
                }
            });
            fail("This should have thrown an exception");
        } catch(BridgeServiceException e) {
            assertEquals("There has been a problem retrieving the study", e.getMessage());
        }
    }
    
    @Test
    public void somethingIsCached() throws Exception {
        
        String originalStudyJson = mapper.writeValueAsString(study);
        
        ViewCache cache = new ViewCache();
        ViewCacheKey<Study> cacheKey = cache.getCacheKey(Study.class, study.getIdentifier());
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getString(cacheKey.getKey())).thenReturn(originalStudyJson);
        cache.setCacheProvider(provider);
        
        String json = cache.getView(cacheKey, new Supplier<Study>() {
            @Override public Study get() {
                fail("This should not be called");
                return null;
            }
        });
        
        Study foundStudy = BridgeObjectMapper.get().readValue(json, DynamoStudy.class);
        assertEquals("Test Study", foundStudy.getName());
    }
    
    @Test
    public void removeFromCacheWorks() throws Exception {
        
        final String originalStudyJson = mapper.writeValueAsString(study);
        ViewCache cache = new ViewCache();
        final ViewCacheKey<Study> cacheKey = cache.getCacheKey(Study.class, study.getIdentifier());
        cache.setCacheProvider(getSimpleCacheProvider(cacheKey.getKey(), originalStudyJson));
        
        cache.removeView(cacheKey);
        
        String json = cache.getView(cacheKey, new Supplier<Study>() {
            @Override public Study get() {
                Study study = TestUtils.getValidStudy();
                study.setName("Test Study 2");
                return study;
            }
        });
        Study foundStudy = BridgeObjectMapper.get().readValue(json, DynamoStudy.class);
        assertEquals("Test Study 2", foundStudy.getName());
    }
    
    @Test
    public void getCacheKeyWorks() {
        ViewCache cache = new ViewCache();
        
        ViewCacheKey<Study> cacheKey = cache.getCacheKey(Study.class, "mostRandom", "leastRandom");
        assertEquals("mostRandom:leastRandom:org.sagebionetworks.bridge.models.studies.Study:view", cacheKey.getKey());
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
            public void setString(String cacheKey, String value) {
                map.put(cacheKey, value);
            }
            public void removeString(String cacheKey) {
                map.remove(cacheKey);
            }
        };   
    }
    
}
