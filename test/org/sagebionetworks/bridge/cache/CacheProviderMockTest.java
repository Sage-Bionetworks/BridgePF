package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;

import redis.clients.jedis.JedisPool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class CacheProviderMockTest {

    private static final CacheKey CACHE_KEY = CacheKey.study("key");
    private static final Encryptor ENCRYPTOR = new AesGcmEncryptor(BridgeConfigFactory.getConfig().getProperty("bridge.healthcode.redis.key"));
    private static final String USER_ID = "userId";
    private static final String ENCRYPTED_SESSION_TOKEN = "TFMkaVFKPD48WissX0bgcD3esBMEshxb3MVgKxHnkXLSEPN4FQMKc01tDbBAVcXx94kMX6ckXVYUZ8wx4iICl08uE+oQr9gorE1hlgAyLAM=";
    private static final String DECRYPTED_SESSION_TOKEN = "ccea2978-f5b9-4377-8194-f887a3e2a19b";
    private static final CacheKey TOKEN_TO_USER_ID = CacheKey.tokenToUserId(DECRYPTED_SESSION_TOKEN);
    private static final CacheKey USER_ID_TO_SESSION = CacheKey.userIdToSession(USER_ID);

    private CacheProvider cacheProvider;
    
    @Mock
    private JedisTransaction transaction;

    @Mock
    private JedisOps jedisOps;
    
    @Test
    public void addAndRemoveViewFromCacheProvider() throws Exception {
        final CacheProvider simpleCacheProvider = new CacheProvider();
        simpleCacheProvider.setJedisOps(getJedisOps());

        final Study study = TestUtils.getValidStudy(CacheProviderMockTest.class);
        study.setIdentifier("test");
        study.setName("This is a test study");
        String json = BridgeObjectMapper.get().writeValueAsString(study);
        assertTrue(json != null && json.length() > 0);

        final CacheKey cacheKey = CacheKey.study(study.getIdentifier());
        simpleCacheProvider.setObject(cacheKey, json, BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);

        String cachedString = simpleCacheProvider.getObject(cacheKey, String.class);
        assertEquals(json, cachedString);

        // Remove something that's not the key
        final CacheKey brokenCacheKey = CacheKey.study(study.getIdentifier()+"2");
        simpleCacheProvider.removeObject(brokenCacheKey);
        cachedString = simpleCacheProvider.getObject(cacheKey, String.class);
        assertEquals(json, cachedString);

        simpleCacheProvider.removeObject(cacheKey);
        cachedString = simpleCacheProvider.getObject(cacheKey, String.class);
        assertNull(cachedString);
    }

    @Test
    public void addToSet() {
        cacheProvider.addCacheKeyToSet(CACHE_KEY, "member");
        
        verify(jedisOps).sadd(CACHE_KEY.toString(), "member");
    }

    private void assertSession(String json) {
        JedisOps jedisOps = mock(JedisOps.class);
        
        when(jedisOps.get(TOKEN_TO_USER_ID.toString())).thenReturn(USER_ID);
        when(jedisOps.get(USER_ID_TO_SESSION.toString())).thenReturn(json);
        
        cacheProvider.setJedisOps(jedisOps);
        
        UserSession session = cacheProvider.getUserSession(DECRYPTED_SESSION_TOKEN);

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
        assertEquals(ImmutableList.of("en","fr"), participant.getLanguages());
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

    @Before
    public void before() {
        mockTransaction(transaction);
        
        when(jedisOps.getTransaction()).thenReturn(transaction);
        when(jedisOps.get(TOKEN_TO_USER_ID.toString())).thenReturn(USER_ID);
        
        cacheProvider = new CacheProvider();
        cacheProvider.setJedisOps(jedisOps);
    }

    private UserSession createUserSession() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("userEmail")
                .withId(USER_ID)
                .withHealthCode("healthCode").build();        
        UserSession session = new UserSession(participant);
        session.setSessionToken(DECRYPTED_SESSION_TOKEN);
        return session;
    }

    @Test
    public void emptySetDoesNotDelete() {
        doReturn(Sets.newHashSet()).when(jedisOps).smembers(CACHE_KEY.toString());
        // */doReturn(transaction).when(jedisOps).getTransaction(CACHE_KEY.toString());
        
        cacheProvider.removeSetOfCacheKeys(CACHE_KEY);
        verify(transaction, never()).del(anyString());
        verify(transaction, never()).exec();
    }

    private JedisOps getJedisOps() {
        return new JedisOps(new JedisPool()) {
            private Map<String,String> map = Maps.newHashMap();
            @Override
            public Long del(final String... keys) {
                for (String key : keys) {
                    map.remove(key);
                }
                return (long)keys.length;
            }
            @Override
            public Long expire(final String key, final int seconds) {
                return 1L;
            }
            @Override
            public String get(final String key) {
                return map.get(key);
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
        };
    }

    @Test
    public void getObject() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", "callbackUrl");
        String ser = BridgeObjectMapper.get().writeValueAsString(provider);
        when(jedisOps.get(CACHE_KEY.toString())).thenReturn(ser);
        
        OAuthProvider returned = cacheProvider.getObject(CACHE_KEY, OAuthProvider.class);
        assertEquals(provider, returned);
        verify(jedisOps).get(CACHE_KEY.toString());
    }
    
    @Test
    public void getObjectOfString() throws Exception {
        String ser = BridgeObjectMapper.get().writeValueAsString("Test");
        when(jedisOps.get(CACHE_KEY.toString())).thenReturn(ser);
        
        String result = cacheProvider.getObject(CACHE_KEY, String.class);
        assertEquals("Test", result);
        verify(jedisOps).get(CACHE_KEY.toString());
    }
    
    @Test
    public void getObjectWithReexpire() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", "callbackUrl");
        String ser = BridgeObjectMapper.get().writeValueAsString(provider);
        when(jedisOps.get(CACHE_KEY.toString())).thenReturn(ser);
        
        OAuthProvider returned = cacheProvider.getObject(CACHE_KEY, OAuthProvider.class, 100);
        assertEquals(provider, returned);
        verify(jedisOps).get(CACHE_KEY.toString());
        verify(jedisOps).expire(CACHE_KEY.toString(), 100);
    }
    
    @Test
    public void getObjectWithReexpireOfString() throws Exception {
        String ser = BridgeObjectMapper.get().writeValueAsString("Test");
        when(jedisOps.get(CACHE_KEY.toString())).thenReturn(ser);
        
        String result = cacheProvider.getObject(CACHE_KEY, String.class, 100);
        assertEquals("Test", result);
        verify(jedisOps).expire(CACHE_KEY.toString(), 100);
    }
    
    @Test
    public void getObjectWithTypeReference() throws Exception {
        OAuthProvider provider1 = new OAuthProvider("clientId1", "secret1", "endpoint1", "callbackUrl1");
        OAuthProvider provider2 = new OAuthProvider("clientId2", "secret2", "endpoint2", "callbackUrl2");
        List<OAuthProvider> providers = Lists.newArrayList(provider1, provider2);
        String ser = BridgeObjectMapper.get().writeValueAsString(providers);
        when(jedisOps.get(CACHE_KEY.toString())).thenReturn(ser);
        
        TypeReference<List<OAuthProvider>> typeRef = new TypeReference<List<OAuthProvider>>() {};
        
        List<OAuthProvider> returned = cacheProvider.getObject(CACHE_KEY, typeRef);
        assertEquals(provider1, returned.get(0));
        assertEquals(provider2, returned.get(1));
        assertEquals(2, returned.size());
    }

    @Test
    public void getUserSessionByUserIdNewVersionUserHasNoSession() {
        // No session returned, null returned
        UserSession retrieved = cacheProvider.getUserSessionByUserId(USER_ID);
        assertNull(retrieved);
    }

    @Test
    public void getUserSessionByUserIdNewVersionUserHasSession() throws Exception {
        UserSession session = new UserSession();
        session.setSessionToken(DECRYPTED_SESSION_TOKEN);
        String ser = BridgeObjectMapper.get().writeValueAsString(session);
        when(jedisOps.get(USER_ID_TO_SESSION.toString())).thenReturn(ser);
        
        UserSession retrieved = cacheProvider.getUserSessionByUserId(USER_ID);
        assertEquals(DECRYPTED_SESSION_TOKEN, retrieved.getSessionToken());
    }

    @Test
    public void getUserSessionSessionTokenMismatch() throws Exception {
        UserSession session = new UserSession(new StudyParticipant.Builder().build());
        session.setSessionToken("notTheSessionTokenWereLookingFor");
        
        when(jedisOps.get(USER_ID_TO_SESSION.toString()))
                .thenReturn(BridgeObjectMapper.get().writeValueAsString(session));

        UserSession retrieved = cacheProvider.getUserSession(DECRYPTED_SESSION_TOKEN);
        assertNull(retrieved);
    }

    @Test
    public void getUserSessionSuccessful() throws Exception {
        UserSession session = new UserSession(new StudyParticipant.Builder().build());
        session.setSessionToken(DECRYPTED_SESSION_TOKEN);
        
        when(jedisOps.get(USER_ID_TO_SESSION.toString()))
                .thenReturn(BridgeObjectMapper.get().writeValueAsString(session));

        UserSession retrieved = cacheProvider.getUserSession(DECRYPTED_SESSION_TOKEN);
        assertEquals(session.getSessionToken(), retrieved.getSessionToken());
    }
    
    @Test
    public void getUserSessionTokenNotFound() {
        // When nothing is mocked, the session token is not found
        reset(jedisOps);
        
        UserSession retrieved = cacheProvider.getUserSession(DECRYPTED_SESSION_TOKEN);
        assertNull(retrieved);
    }
    
    @Test
    public void getUserSessionUserHasNoSession() {
        // When token --> userId mapping is mocked, but there's no session, return null
        UserSession retrieved = cacheProvider.getUserSession(DECRYPTED_SESSION_TOKEN);
        assertNull(retrieved);
    }
    
    private void mockTransaction(JedisTransaction trans) {
        when(trans.setex(any(String.class), anyInt(), any(String.class))).thenReturn(trans);
        // */when(trans.expire(any(String.class), anyInt())).thenReturn(trans);
        when(trans.del(any(String.class))).thenReturn(trans);
        when(trans.exec()).thenReturn(Arrays.asList((Object)"OK", "OK"));
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
    public void nullSetDoesNotDelete() {
        doReturn(null).when(jedisOps).smembers(CACHE_KEY.toString());
        // */doReturn(transaction).when(jedisOps).getTransaction(CACHE_KEY.toString());
        
        cacheProvider.removeSetOfCacheKeys(CACHE_KEY);
        verify(transaction, never()).del(anyString());
        verify(transaction, never()).exec();
    }

    @Test
    public void removeSetOfCacheKeys() {
        doReturn(Sets.newHashSet("key1", "key2")).when(jedisOps).smembers(CACHE_KEY.toString());
        // */doReturn(transaction).when(jedisOps).getTransaction(CACHE_KEY.toString());
        
        cacheProvider.removeSetOfCacheKeys(CACHE_KEY);
        verify(transaction).del("key1");
        verify(transaction).del("key2");
        verify(transaction).del(CACHE_KEY.toString());
        verify(transaction).exec();
    }
    
    @Test
    public void setObject() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", "callbackUrl");
        String ser = BridgeObjectMapper.get().writeValueAsString(provider);
        when(jedisOps.set(CACHE_KEY.toString(), ser)).thenReturn("OK");
        
        cacheProvider.setObject(CACHE_KEY, provider);
        verify(jedisOps).set(CACHE_KEY.toString(), ser);
    }
    
    @Test
    public void setObjectOfString() {
        when(jedisOps.set(CACHE_KEY.toString(), "\"test\"")).thenReturn("OK");
        
        cacheProvider.setObject(CACHE_KEY, "test");
        verify(jedisOps).set(CACHE_KEY.toString(), "\"test\"");
    }
    
    @Test
    public void setObjectWithExpire() throws Exception {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", "callbackUrl");
        String ser = BridgeObjectMapper.get().writeValueAsString(provider);
        when(jedisOps.setex(CACHE_KEY.toString(), 100, ser)).thenReturn("OK");
        
        cacheProvider.setObject(CACHE_KEY, provider, 100);
        verify(jedisOps).setex(CACHE_KEY.toString(), 100, ser);
    }
    
    @Test
    public void testGetUserSessionByUserId() throws Exception {
        CacheProvider mockCacheProvider = spy(cacheProvider);
        mockCacheProvider.getUserSessionByUserId(USER_ID);
        
        verify(jedisOps).get("userId:session2:user");
    }
    
    @Test
    public void testRemoveSession() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).build();

        UserSession session = new UserSession(participant);
        session.setSessionToken(DECRYPTED_SESSION_TOKEN);
        
        cacheProvider.removeSession(session);
        cacheProvider.getUserSession(DECRYPTED_SESSION_TOKEN);
        
        verify(transaction).del(TOKEN_TO_USER_ID.toString());
        verify(transaction).del(USER_ID_TO_SESSION.toString());
        verify(transaction).exec();
    }

    @Test
    public void testRemoveSessionByUserId() throws Exception {
        UserSession session = createUserSession();
        String ser = BridgeObjectMapper.get().writeValueAsString(session);

        when(jedisOps.get(USER_ID_TO_SESSION.toString())).thenReturn(ser);
        
        cacheProvider.removeSessionByUserId(USER_ID);
        
        verify(transaction).del(TOKEN_TO_USER_ID.toString());
        verify(transaction).del(USER_ID_TO_SESSION.toString());
        verify(transaction).exec();
    }
    
    @Test
    public void testSetUserSession() throws Exception {
        UserSession session = createUserSession();
        cacheProvider.setUserSession(session);
        
        verify(transaction).setex(eq(TOKEN_TO_USER_ID.toString()), anyInt(), eq(USER_ID));
        verify(transaction).setex(eq(USER_ID_TO_SESSION.toString()), anyInt(), anyString());
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
        verify(transaction, never()).setex(eq(TOKEN_TO_USER_ID.toString()), anyInt(), anyString());
        verify(transaction, never()).setex(eq(USER_ID_TO_SESSION.toString()), anyInt(), eq(DECRYPTED_SESSION_TOKEN));
        verify(transaction, never()).exec();
    }
    
    @Test
    public void testSetUserSessionNullUser() throws Exception {
        UserSession session = new UserSession();
        session.setSessionToken(DECRYPTED_SESSION_TOKEN);
        try {
            cacheProvider.setUserSession(session);
        } catch(NullPointerException e) {
            assertTrue("NPE expected.", true);
        } catch(Throwable e) {
            fail(e.getMessage());
        }
        verify(transaction, never()).setex(eq(TOKEN_TO_USER_ID.toString()), anyInt(), anyString());
        verify(transaction, never()).setex(eq(USER_ID_TO_SESSION.toString()), anyInt(), eq(DECRYPTED_SESSION_TOKEN));
        verify(transaction, never()).exec();
    }
    
    @Test
    public void testSetUserSessionNullUserId() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("userEmail")
                .withHealthCode("healthCode").build();        
        
        UserSession session = new UserSession(participant);
        session.setSessionToken(DECRYPTED_SESSION_TOKEN);
        try {
            cacheProvider.setUserSession(session);
        } catch(NullPointerException e) {
            assertTrue("NPE expected.", true);
        } catch(Throwable e) {
            fail(e.getMessage());
        }
        verify(transaction, never()).setex(eq(TOKEN_TO_USER_ID.toString()), anyInt(), anyString());
        verify(transaction, never()).setex(eq(USER_ID_TO_SESSION.toString()), anyInt(), eq(DECRYPTED_SESSION_TOKEN));
        verify(transaction, never()).exec();
    }
}
