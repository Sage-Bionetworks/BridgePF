package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

import com.google.common.collect.Sets;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class CacheAdminServiceTest {

    private static final String REQUEST_INFO_KEY = "10E9SFUz9BYrqCrTzfiaNW:request-info";
    
    private final static Set<String> KEYS = Sets.newHashSet("foo:study", "bar:session", "baz:Survey:view",
            "xh7YDmjGQuTKnfdv9iJb0:session:user", REQUEST_INFO_KEY);
    
    private CacheAdminService adminService;
    
    @Before
    public void before() {
        adminService = new CacheAdminService();
        
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(createStubJedis());

        adminService.setJedisPool(pool);
    }
    
    @Test
    public void listsItemsWithoutSessions() {
        Set<String> set = adminService.listItems();
        assertEquals(2, set.size());
        assertTrue(set.contains("foo:study"));
        assertTrue(set.contains("baz:Survey:view"));
    }
    
    
    @Test
    public void canRemoveItem() {
        adminService.removeItem("foo:study");
        Set<String> set = adminService.listItems();
        assertEquals(1, set.size());
    }
    
    @Test(expected = BridgeServiceException.class)
    public void doesNotRemoveSessions() {
        adminService.removeItem("bar:session");
    }
    
    @Test(expected = BridgeServiceException.class)
    public void doesNotRemoveUserSessions() {
        adminService.removeItem("xh7YDmjGQuTKnfdv9iJb0:session:user");
    }
    
    @Test(expected = BridgeServiceException.class)
    public void throwsExceptionWhenThereIsNoKey() {
        adminService.removeItem("not:a:key");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void throwsExceptionWhenKeyIsEmpty() {
        adminService.removeItem(" ");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void throwsExceptionWhenKeyIsNull() {
        adminService.removeItem(null);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void cannotRemoveRequestInfo() {
        adminService.removeItem(REQUEST_INFO_KEY);
    }
    
    private Jedis createStubJedis() {
        return new Jedis("") {
            @Override
            public Set<String> keys(String pattern) {
                return KEYS;
            }
            @Override
            public Long del(String key) {
                return (KEYS.remove(key)) ? 1L : 0L;
            }
        };
    }
}
