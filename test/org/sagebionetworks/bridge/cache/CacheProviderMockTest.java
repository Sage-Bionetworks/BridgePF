package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;
import org.sagebionetworks.bridge.redis.RedisKey;

import redis.clients.jedis.JedisPool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class CacheProviderMockTest {

    private static final String CACHE_KEY = "key";
    private static final Encryptor ENCRYPTOR = new AesGcmEncryptor(BridgeConfigFactory.getConfig().getProperty("bridge.healthcode.redis.key"));
    private static final String USER_ID = "userId";
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String ENCRYPTED_SESSION_TOKEN = "TFMkaVFKPD48WissX0bgcD3esBMEshxb3MVgKxHnkXLSEPN4FQMKc01tDbBAVcXx94kMX6ckXVYUZ8wx4iICl08uE+oQr9gorE1hlgAyLAM=";
    private static final String DECRYPTED_SESSION_TOKEN = "ccea2978-f5b9-4377-8194-f887a3e2a19b";
    
    private CacheProvider cacheProvider;
    
    @Mock
    private JedisTransaction transaction;

    @Mock
    private JedisOps jedisOps;
    
    @Before
    public void before() {
        mockTransaction(transaction);
        
        when(jedisOps.getTransaction()).thenReturn(transaction);
        
        String userKey = RedisKey.USER_SESSION.getRedisKey(USER_ID);
        when(jedisOps.get(userKey)).thenReturn(SESSION_TOKEN);
        
        cacheProvider = new CacheProvider();
        cacheProvider.setJedisOps(jedisOps);
        cacheProvider.setBridgeObjectMapper(BridgeObjectMapper.get());
    }

    private void mockTransaction(JedisTransaction trans) {
        when(trans.setex(any(String.class), anyInt(), any(String.class))).thenReturn(trans);
        when(trans.expire(any(String.class), anyInt())).thenReturn(trans);
        when(trans.del(any(String.class))).thenReturn(trans);
        when(trans.exec()).thenReturn(Arrays.asList((Object)"OK", "OK"));
    }

    @Test
    public void testSetUserSession() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("userEmail")
                .withId(USER_ID)
                .withHealthCode("healthCode").build();
        
        UserSession session = new UserSession(participant);
        session.setSessionToken(SESSION_TOKEN);
        cacheProvider.setUserSession(session);

        String sessionKey = RedisKey.SESSION.getRedisKey(SESSION_TOKEN);
        String userKey = RedisKey.USER_SESSION.getRedisKey(USER_ID);
        
        verify(transaction).setex(eq(sessionKey), anyInt(), anyString());
        verify(transaction).setex(eq(userKey), anyInt(), eq(SESSION_TOKEN));
        verify(transaction).exec();
    }

    @Test
    public void testSetUserSessionNullSessionToken() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("userEmail")
                .withId(USER_ID)
                .withHealthCode("healthCode").build();
        
        UserSession session = new UserSession(participant);
        try {
            cacheProvider.setUserSession(session);
        } catch(NullPointerException e) {
            assertTrue("NPE expected.", true);
        } catch(Throwable e) {
            fail(e.getMessage());
        }
        String sessionKey = RedisKey.SESSION.getRedisKey(SESSION_TOKEN);
        String userKey = RedisKey.USER_SESSION.getRedisKey(USER_ID);
        
        verify(transaction, never()).setex(eq(sessionKey), anyInt(), anyString());
        verify(transaction, never()).setex(eq(userKey), anyInt(), eq(SESSION_TOKEN));
        verify(transaction, never()).exec();
    }

    @Test
    public void testSetUserSessionNullUser() throws Exception {
        UserSession session = new UserSession();
        session.setSessionToken(SESSION_TOKEN);
        try {
            cacheProvider.setUserSession(session);
        } catch(NullPointerException e) {
            assertTrue("NPE expected.", true);
        } catch(Throwable e) {
            fail(e.getMessage());
        }
        String sessionKey = RedisKey.SESSION.getRedisKey(SESSION_TOKEN);
        String userKey = RedisKey.USER_SESSION.getRedisKey(USER_ID);
        
        verify(transaction, never()).setex(eq(sessionKey), anyInt(), anyString());
        verify(transaction, never()).setex(eq(userKey), anyInt(), eq(SESSION_TOKEN));
        verify(transaction, never()).exec();
    }

    @Test
    public void testSetUserSessionNullUserId() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("userEmail")
                .withHealthCode("healthCode").build();        
        
        UserSession session = new UserSession(participant);
        session.setSessionToken(SESSION_TOKEN);
        try {
            cacheProvider.setUserSession(session);
        } catch(NullPointerException e) {
            assertTrue("NPE expected.", true);
        } catch(Throwable e) {
            fail(e.getMessage());
        }
        String sessionKey = RedisKey.SESSION.getRedisKey(SESSION_TOKEN);
        String userKey = RedisKey.USER_SESSION.getRedisKey(USER_ID);
        
        verify(transaction, never()).setex(eq(sessionKey), anyInt(), anyString());
        verify(transaction, never()).setex(eq(userKey), anyInt(), eq(SESSION_TOKEN));
        verify(transaction, never()).exec();
    }

    @Test
    public void testGetUserSessionByUserId() throws Exception {
        CacheProvider mockCacheProvider = spy(cacheProvider);
        mockCacheProvider.getUserSessionByUserId(USER_ID);
        
        verify(jedisOps).get("userId:session:user");
        verify(jedisOps).get("sessionToken:session");
    }

    @Test
    public void testRemoveSession() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).build();

        UserSession session = new UserSession(participant);
        session.setSessionToken(SESSION_TOKEN);
        
        cacheProvider.removeSession(session);
        cacheProvider.getUserSession(SESSION_TOKEN);
        String sessionKey = RedisKey.SESSION.getRedisKey(SESSION_TOKEN);
        String userKey = RedisKey.USER_SESSION.getRedisKey(USER_ID);
        
        verify(transaction).del(sessionKey);
        verify(transaction).del(userKey);
        verify(transaction).exec();
    }

    @Test
    public void testRemoveSessionByUserId() {
        cacheProvider.removeSessionByUserId(USER_ID);
        String sessionKey = RedisKey.SESSION.getRedisKey(SESSION_TOKEN);
        String userKey = RedisKey.USER_SESSION.getRedisKey(USER_ID);
        
        verify(transaction).del(sessionKey);
        verify(transaction).del(userKey);
        verify(transaction).exec();
    }

    @Test
    public void addAndRemoveViewFromCacheProvider() throws Exception {
        final CacheProvider simpleCacheProvider = new CacheProvider();
        simpleCacheProvider.setJedisOps(getJedisOps());
        simpleCacheProvider.setBridgeObjectMapper(BridgeObjectMapper.get());

        final Study study = TestUtils.getValidStudy(CacheProviderMockTest.class);
        study.setIdentifier("test");
        study.setName("This is a test study");
        String json = BridgeObjectMapper.get().writeValueAsString(study);
        assertTrue(json != null && json.length() > 0);

        final String cacheKey = study.getIdentifier() + ":Study";
        simpleCacheProvider.setObject(cacheKey, json, BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);

        String cachedString = simpleCacheProvider.getObject(cacheKey, String.class);
        assertEquals(json, cachedString);

        // Remove something that's not the key
        simpleCacheProvider.removeObject(cacheKey+"2");
        cachedString = simpleCacheProvider.getObject(cacheKey, String.class);
        assertEquals(json, cachedString);

        simpleCacheProvider.removeObject(cacheKey);
        cachedString = simpleCacheProvider.getObject(cacheKey, String.class);
        assertNull(cachedString);
    }

    @Test
    public void newUserSessionDeserializes() {
        String json = TestUtils.createJson("{'authenticated':true,"+
                "'environment':'local',"+
                "'sessionToken':'"+DECRYPTED_SESSION_TOKEN+"',"+
                "'internalSessionToken':'4f0937a5-6ebf-451b-84bc-fbf649b9e93c',"+
                "'studyIdentifier':{'identifier':'api',"+
                    "'type':'StudyIdentifier'},"+
                "'consentStatuses':{"+
                    "'api':{'name':'Default Consent Group',"+
                        "'subpopulationGuid':'api',"+
                        "'required':true,"+
                        "'consented':false,"+
                        "'signedMostRecentConsent':true,"+
                        "'type':'ConsentStatus'}},"+
                "'participant':{'firstName':'Bridge',"+
                    "'lastName':'IT',"+
                    "'email':'bridgeit@sagebase.org',"+
                    "'sharingScope':'no_sharing',"+
                    "'notifyByEmail':false,"+
                    "'externalId':'ABC',"+
                    "'dataGroups':['group1'],"+
                    "'encryptedHealthCode':'"+ENCRYPTED_SESSION_TOKEN+"',"+
                    "'attributes':{},"+
                    "'consentHistories':{},"+
                    "'roles':['admin'],"+
                    "'languages':['en','fr'],"+
                    "'createdOn':'2016-04-21T16:48:22.386Z',"+
                    "'id':'6gq4jGXLmAxVbLLmVifKN4',"+
                    "'type':'StudyParticipant'},"+
                "'type':'UserSession'}");

        assertSession(json);
    }
    
    @Test
    public void getObject() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", "callbackUrl");
        String ser = BridgeObjectMapper.get().writeValueAsString(provider);
        when(jedisOps.get(CACHE_KEY)).thenReturn(ser);
        
        OAuthProvider returned = cacheProvider.getObject(CACHE_KEY, OAuthProvider.class);
        assertEquals(provider, returned);
        verify(jedisOps).get(CACHE_KEY);
    }
    
    @Test
    public void getObjectWithTypeReference() throws Exception {
        OAuthProvider provider1 = new OAuthProvider("clientId1", "secret1", "endpoint1", "callbackUrl1");
        OAuthProvider provider2 = new OAuthProvider("clientId2", "secret2", "endpoint2", "callbackUrl2");
        List<OAuthProvider> providers = Lists.newArrayList(provider1, provider2);
        String ser = BridgeObjectMapper.get().writeValueAsString(providers);
        when(jedisOps.get(CACHE_KEY)).thenReturn(ser);
        
        TypeReference<List<OAuthProvider>> typeRef = new TypeReference<List<OAuthProvider>>() {};
        
        List<OAuthProvider> returned = cacheProvider.getObject(CACHE_KEY, typeRef);
        assertEquals(provider1, returned.get(0));
        assertEquals(provider2, returned.get(1));
        assertEquals(2, returned.size());
    }
    
    @Test
    public void getObjectWithReexpire() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", "callbackUrl");
        String ser = BridgeObjectMapper.get().writeValueAsString(provider);
        when(jedisOps.get(CACHE_KEY)).thenReturn(ser);
        
        OAuthProvider returned = cacheProvider.getObject(CACHE_KEY, OAuthProvider.class, 100);
        assertEquals(provider, returned);
        verify(jedisOps).get(CACHE_KEY);
        verify(jedisOps).expire(CACHE_KEY, 100);
    }
    
    @Test
    public void setObject() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", "callbackUrl");
        String ser = BridgeObjectMapper.get().writeValueAsString(provider);
        when(jedisOps.set(CACHE_KEY, ser)).thenReturn("OK");
        
        cacheProvider.setObject(CACHE_KEY, provider);
        verify(jedisOps).set(CACHE_KEY, ser);
    }
    
    @Test
    public void setObjectWithExpire() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", "callbackUrl");
        String ser = BridgeObjectMapper.get().writeValueAsString(provider);
        when(jedisOps.setex(CACHE_KEY, 100, ser)).thenReturn("OK");
        
        cacheProvider.setObject(CACHE_KEY, provider, 100);
        verify(jedisOps).setex(CACHE_KEY, 100, ser);
    }

    @Test
    public void getObjectOfString() throws Exception {
        String ser = BridgeObjectMapper.get().writeValueAsString("Test");
        when(jedisOps.get(CACHE_KEY)).thenReturn(ser);
        
        String result = cacheProvider.getObject(CACHE_KEY, String.class);
        assertEquals("Test", result);
        verify(jedisOps).get(CACHE_KEY);
    }
    
    @Test
    public void getObjectWithReexpireOfString() throws Exception {
        String ser = BridgeObjectMapper.get().writeValueAsString("Test");
        when(jedisOps.get(CACHE_KEY)).thenReturn(ser);
        
        String result = cacheProvider.getObject(CACHE_KEY, String.class, 100);
        assertEquals("Test", result);
        verify(jedisOps).expire(CACHE_KEY, 100);
    }
    
    @Test
    public void setObjectWithReexpireOfString() {
        when(jedisOps.set(CACHE_KEY, "\"test\"")).thenReturn("OK");
        
        cacheProvider.setObject(CACHE_KEY, "test");
        verify(jedisOps).set(CACHE_KEY, "\"test\"");
    }
    
    @Test
    public void setObjectOfString() {
    }
    

    private void assertSession(String json) {
        JedisOps jedisOps = mock(JedisOps.class);
        
        String sessionKey = RedisKey.SESSION.getRedisKey("sessionToken");
        doReturn(sessionKey).when(jedisOps).get("sessionToken");
        doReturn(transaction).when(jedisOps).getTransaction(sessionKey);
        doReturn(json).when(jedisOps).get(sessionKey);
        
        cacheProvider.setJedisOps(jedisOps);
        cacheProvider.setBridgeObjectMapper(BridgeObjectMapper.get());
        
        UserSession session = cacheProvider.getUserSession("sessionToken");

        assertTrue(session.isAuthenticated());
        assertEquals(Environment.LOCAL, session.getEnvironment());
        assertEquals(DECRYPTED_SESSION_TOKEN, session.getSessionToken());
        assertEquals("4f0937a5-6ebf-451b-84bc-fbf649b9e93c", session.getInternalSessionToken());
        assertEquals("6gq4jGXLmAxVbLLmVifKN4", session.getId());
        assertEquals("api", session.getStudyIdentifier().getIdentifier());
        
        StudyParticipant participant = session.getParticipant();
        assertEquals("Bridge", participant.getFirstName());
        assertEquals("IT", participant.getLastName());
        assertEquals("bridgeit@sagebase.org", participant.getEmail());
        assertEquals(SharingScope.NO_SHARING, participant.getSharingScope());
        assertEquals(DateTime.parse("2016-04-21T16:48:22.386Z"), participant.getCreatedOn());
        assertEquals(Sets.newHashSet(Roles.ADMIN), participant.getRoles());
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
        assertEquals("ABC", participant.getExternalId());
        
        assertEquals(participant.getHealthCode(), ENCRYPTOR.decrypt(ENCRYPTED_SESSION_TOKEN));
        
        SubpopulationGuid apiGuid = SubpopulationGuid.create("api");
        Map<SubpopulationGuid,ConsentStatus> consentStatuses = session.getConsentStatuses();
        ConsentStatus status = consentStatuses.get(apiGuid);
        assertEquals("Default Consent Group", status.getName());
        assertEquals(apiGuid.getGuid(), status.getSubpopulationGuid());
        assertTrue(status.getSignedMostRecentConsent());
        assertTrue(status.isRequired());
        assertFalse(status.isConsented());
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
