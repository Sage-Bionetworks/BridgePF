package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;

public class ViewCacheTest {
    
    private BridgeObjectMapper mapper;
    private Study study;
    
    @Before
    public void before() {
        mapper = BridgeObjectMapper.get();
        
        study = TestUtils.getValidStudy(ViewCacheTest.class);
    }
    
    @Test
    public void nothingWasCached() throws Exception {
        ViewCache cache = new ViewCache();
        cache.setObjectMapper(BridgeObjectMapper.get());
        cache.setCachePeriod(BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        ViewCacheKey<Study> cacheKey = cache.getCacheKey(Study.class, study.getIdentifier());
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getObject(cacheKey.getKey(), String.class)).thenReturn(null);
        cache.setCacheProvider(provider);
        
        String json = cache.getView(cacheKey, new Supplier<Study>() {
            @Override public Study get() {
                Study study = TestUtils.getValidStudy(ViewCacheTest.class);
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
        cache.setObjectMapper(BridgeObjectMapper.get());
        cache.setCachePeriod(BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        ViewCacheKey<Study> cacheKey = cache.getCacheKey(Study.class, study.getIdentifier());
        
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getObject(cacheKey.getKey(), String.class)).thenReturn(null);
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
        cache.setObjectMapper(BridgeObjectMapper.get());
        cache.setCachePeriod(BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        ViewCacheKey<Study> cacheKey = cache.getCacheKey(Study.class, study.getIdentifier());
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getObject(cacheKey.getKey(), String.class)).thenReturn(originalStudyJson);
        cache.setCacheProvider(provider);
        
        String json = cache.getView(cacheKey, new Supplier<Study>() {
            @Override public Study get() {
                fail("This should not be called");
                return null;
            }
        });
        
        Study foundStudy = BridgeObjectMapper.get().readValue(json, DynamoStudy.class);
        assertEquals("Test Study [ViewCacheTest]", foundStudy.getName());
    }
    
    @Test
    public void removeFromCacheWorks() throws Exception {
        final String originalStudyJson = mapper.writeValueAsString(study);
        
        ViewCache cache = new ViewCache();
        cache.setObjectMapper(BridgeObjectMapper.get());
        cache.setCachePeriod(BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        final ViewCacheKey<Study> cacheKey = cache.getCacheKey(Study.class, study.getIdentifier());
        cache.setCacheProvider(getSimpleCacheProvider(cacheKey.getKey(), originalStudyJson));
        
        cache.removeView(cacheKey);
        
        String json = cache.getView(cacheKey, new Supplier<Study>() {
            @Override public Study get() {
                Study study = TestUtils.getValidStudy(ViewCacheTest.class);
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
        assertEquals("mostRandom:leastRandom:Study:view", cacheKey.getKey());
    }
    
    @Test
    public void canReconfigureViewCache() throws Exception {
        CacheProvider provider = mock(CacheProvider.class);
        
        Survey survey = Survey.create();
        survey.setIdentifier("config-test");

        ObjectMapper mapper = new ObjectMapper();
        // need this filter config for mapper to work on Survey
        FilterProvider filter = new SimpleFilterProvider().setFailOnUnknownId(false);
        mapper.setFilterProvider(filter);
        
        ViewCache cache = new ViewCache();
        cache.setCachePeriod(1000);
        cache.setObjectMapper(mapper);
        cache.setCacheProvider(provider);
        
        ViewCacheKey<Survey> cacheKey = cache.getCacheKey(Survey.class, survey.getIdentifier());
        cache.getView(cacheKey, () -> survey);
        
        // The string from this mapper doesn't have the "type" attribute, so if this passes, we
        // can be confident that the right mapper has been used.
        verify(provider).setObject(cacheKey.getKey(), mapper.writeValueAsString(survey), 1000);
    }
    
    private CacheProvider getSimpleCacheProvider(final String cacheKey, final String originalStudyJson) {
        return new CacheProvider() {
            private Map<String,String> map = Maps.newHashMap();
            {
                map.put(cacheKey, originalStudyJson);
            }
            public <T> T getObject(String cacheKey, Class<T> clazz) {
                try {
                    return BridgeObjectMapper.get().readValue(map.get(cacheKey), clazz);    
                } catch(Exception e) {
                    return null;
                }
            }
            public void setObject(String cacheKey, Object object) {
                try {
                    String ser = BridgeObjectMapper.get().writeValueAsString(object);
                    map.put(cacheKey, ser);
                } catch(Exception e) {
                }
            }
            public void setObject(String cacheKey, Object object, int secondsUntilExpire) {
                try {
                    String ser = BridgeObjectMapper.get().writeValueAsString(object);
                    map.put(cacheKey, ser);
                } catch(Exception e) {
                }
            }
            public void removeObject(String cacheKey) {
                map.remove(cacheKey);
            }
        };
    }
    
}
