package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;
import org.sagebionetworks.bridge.redis.RedisKey;

import redis.clients.jedis.JedisPool;

import com.google.common.collect.Maps;

public class CacheProviderTest {

    private final String userId = "userId";
    private final String sessionToken = "sessionToken";
    private JedisTransaction transaction;
    private CacheProvider cacheProvider;

    @Before
    public void before() {
        transaction = mock(JedisTransaction.class);
        when(transaction.setex(any(String.class), anyInt(), any(String.class))).thenReturn(transaction);
        when(transaction.expire(any(String.class), anyInt())).thenReturn(transaction);
        when(transaction.del(any(String.class))).thenReturn(transaction);
        when(transaction.exec()).thenReturn(Arrays.asList((Object)"OK", "OK"));
        JedisOps jedisOps = mock(JedisOps.class);
        when(jedisOps.getTransaction()).thenReturn(transaction);
        String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
        when(jedisOps.get(userKey)).thenReturn(sessionToken);
        cacheProvider = new CacheProvider();
        cacheProvider.setJedisOps(jedisOps);
        cacheProvider.setBridgeObjectMapper(BridgeObjectMapper.get());
    }

    @Test
    public void testSetUserSession() throws Exception {
        User user = new User();
        user.setEmail("userEmail");
        user.setId(userId);
        user.setHealthCode("healthCode");
        UserSession session = new UserSession();
        session.setUser(user);
        session.setSessionToken(sessionToken);
        cacheProvider.setUserSession(session);
        String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
        String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
        verify(transaction, times(1)).setex(eq(sessionKey), anyInt(), anyString());
        verify(transaction, times(1)).setex(eq(userKey), anyInt(), eq(sessionToken));
        verify(transaction, times(1)).exec();
    }

    @Test
    public void testSetUserSessionNullSessionToken() throws Exception {
        User user = new User();
        user.setEmail("userEmail");
        user.setId(userId);
        user.setHealthCode("healthCode");
        UserSession session = new UserSession();
        session.setUser(user);
        try {
            cacheProvider.setUserSession(session);
        } catch(NullPointerException e) {
            assertTrue("NPE expected.", true);
        } catch(Throwable e) {
            fail(e.getMessage());
        }
        String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
        String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
        verify(transaction, times(0)).setex(eq(sessionKey), anyInt(), anyString());
        verify(transaction, times(0)).setex(eq(userKey), anyInt(), eq(sessionToken));
        verify(transaction, times(0)).exec();
    }

    @Test
    public void testSetUserSessionNullUser() throws Exception {
        UserSession session = new UserSession();
        session.setSessionToken(sessionToken);
        try {
            cacheProvider.setUserSession(session);
        } catch(NullPointerException e) {
            assertTrue("NPE expected.", true);
        } catch(Throwable e) {
            fail(e.getMessage());
        }
        String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
        String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
        verify(transaction, times(0)).setex(eq(sessionKey), anyInt(), anyString());
        verify(transaction, times(0)).setex(eq(userKey), anyInt(), eq(sessionToken));
        verify(transaction, times(0)).exec();
    }

    @Test
    public void testSetUserSessionNullUserId() throws Exception {
        User user = new User();
        user.setEmail("userEmail");
        user.setHealthCode("healthCode");
        UserSession session = new UserSession();
        session.setUser(user);
        session.setSessionToken(sessionToken);
        try {
            cacheProvider.setUserSession(session);
        } catch(NullPointerException e) {
            assertTrue("NPE expected.", true);
        } catch(Throwable e) {
            fail(e.getMessage());
        }
        String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
        String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
        verify(transaction, times(0)).setex(eq(sessionKey), anyInt(), anyString());
        verify(transaction, times(0)).setex(eq(userKey), anyInt(), eq(sessionToken));
        verify(transaction, times(0)).exec();
    }

    @Test
    public void testGetUserSession() throws Exception {
        String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
        JedisOps jedisOps = mock(JedisOps.class);
        when(jedisOps.getTransaction(sessionKey)).thenReturn(transaction);
        when(jedisOps.get(sessionKey)).thenReturn("userSessionString");
        cacheProvider.setJedisOps(jedisOps);
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(userId);
        UserSession mockUserSession = mock(UserSession.class);
        when(mockUserSession.getUser()).thenReturn(mockUser);
        BridgeObjectMapper mockObjectMapper = mock(BridgeObjectMapper.class);
        when(mockObjectMapper.readValue("userSessionString",  UserSession.class)).thenReturn(mockUserSession);
        cacheProvider.setBridgeObjectMapper(mockObjectMapper);
        cacheProvider.getUserSession(sessionToken);
        String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
        verify(transaction, times(1)).expire(eq(sessionKey), anyInt());
        verify(transaction, times(1)).expire(eq(userKey), anyInt());
        verify(transaction, times(1)).exec();
    }

    @Test
    public void testGetUserSessionByUserId() throws Exception {
        CacheProvider mockCacheProvider = spy(cacheProvider);
        mockCacheProvider.getUserSessionByUserId(userId);
        verify(mockCacheProvider, times(1)).getUserSession(sessionToken);
    }

    @Test
    public void testRemoveSession() {
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(userId);
        UserSession mockUserSession = mock(UserSession.class);
        when(mockUserSession.getUser()).thenReturn(mockUser);
        when(mockUserSession.getSessionToken()).thenReturn(sessionToken);
        cacheProvider.removeSession(mockUserSession);
        cacheProvider.getUserSession(sessionToken);
        String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
        String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
        verify(transaction, times(1)).del(sessionKey);
        verify(transaction, times(1)).del(userKey);
        verify(transaction, times(1)).exec();
    }

    @Test
    public void testRemoveSessionByUserId() {
        cacheProvider.removeSessionByUserId(userId);
        String sessionKey = RedisKey.SESSION.getRedisKey(sessionToken);
        String userKey = RedisKey.USER_SESSION.getRedisKey(userId);
        verify(transaction, times(1)).del(sessionKey);
        verify(transaction, times(1)).del(userKey);
        verify(transaction, times(1)).exec();
    }

    @Test
    public void addAndRemoveViewFromCacheProvider() throws Exception {

        final CacheProvider simpleCacheProvider = new CacheProvider();
        simpleCacheProvider.setJedisOps(getJedisOps());
        simpleCacheProvider.setBridgeObjectMapper(BridgeObjectMapper.get());

        final Study study = TestUtils.getValidStudy(CacheProviderTest.class);
        study.setIdentifier("test");
        study.setName("This is a test study");
        String json = BridgeObjectMapper.get().writeValueAsString(study);
        assertTrue(json != null && json.length() > 0);

        final String cacheKey = study.getIdentifier() + ":Study";
        simpleCacheProvider.setString(cacheKey, json);

        String cachedString = simpleCacheProvider.getString(cacheKey);
        assertEquals(json, cachedString);

        // Remove something that's not the key
        simpleCacheProvider.removeString(cacheKey+"2");
        cachedString = simpleCacheProvider.getString(cacheKey);
        assertEquals(json, cachedString);

        simpleCacheProvider.removeString(cacheKey);
        cachedString = simpleCacheProvider.getString(cacheKey);
        assertNull(cachedString);
    }

    private JedisOps getJedisOps() {
        return new JedisOps(new JedisPool()) {
            private Map<String,String> map = Maps.newHashMap();
            @Override
            public Long expire(final String key, final int seconds) {
                return 1L;
            }
            @Override
            public String setex(final String key, final int seconds, final String value) {
                map.put(key, value);
                return "OK";
            }
            @Override
            public Long setnx(final String key, final String value) {
                map.put(key, value);
                return 1L;
            }
            @Override
            public String get(final String key) {
                return map.get(key);
            }
            @Override
            public Long del(final String... keys) {
                for (String key : keys) {
                    map.remove(key);
                }
                return (long)keys.length;
            }
        };
    }
}
