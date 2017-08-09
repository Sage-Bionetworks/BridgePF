package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.JedisTransaction;

@RunWith(MockitoJUnitRunner.class)
public class CacheProviderMigrationTest {

    private static final String USER_ID = "userId";

    private static final String STUDY_ID = "studyId";

    private static final String REQUEST_INFO_KEY = "userId:request-info";

    private static final String STUDY_ID_KEY = "studyId:study";

    private static final String SESSION_TOKEN = "sessionToken";

    private static final String USER_ID_SESSION_KEY = "userId:session:user";

    private static final String SESSION_TOKEN_KEY = "sessionToken:session";

    private BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    
    private CacheProvider cacheProvider;
    
    @Mock
    private JedisOps oldJedisOps;
    
    @Mock
    private JedisOps newJedisOps;
    
    @Mock
    private JedisTransaction newTransaction;
    
    @Mock
    private JedisTransaction oldTransaction;
    
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    
    UserSession session;
    
    @Before
    public void before() {
        cacheProvider = new CacheProvider();
        cacheProvider.setBridgeObjectMapper(BridgeObjectMapper.get());
        cacheProvider.setJedisOps(oldJedisOps);
        cacheProvider.setNewJedisOps(newJedisOps);
        cacheProvider.setSessionExpireInSeconds(10);
        
        when(newJedisOps.getTransaction()).thenReturn(newTransaction);    
        doReturn(newTransaction).when(newTransaction).setex(any(), anyInt(), any());
        doReturn(newTransaction).when(newTransaction).del(any());

        when(oldJedisOps.getTransaction()).thenReturn(oldTransaction);    
        doReturn(oldTransaction).when(oldTransaction).setex(any(), anyInt(), any());
        doReturn(oldTransaction).when(oldTransaction).del(any());
        
        session = stubSession(USER_ID);
    }
    
    @Test
    public void updateRequestInfoEmptyCache() {
        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();
        
        cacheProvider.updateRequestInfo(info);
        
        verify(newJedisOps).get(any());
        verify(oldJedisOps).get(any());
        verify(newJedisOps).set(any(), any());
        verify(oldJedisOps, never()).set(any(), any());
    }
    
    @Test
    public void updateRequestInfoOldCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();
        
        RequestInfo old = new RequestInfo.Builder().withUserId("oldUserId").build();
        when(oldJedisOps.get(any())).thenReturn(MAPPER.writeValueAsString(old));
        
        cacheProvider.updateRequestInfo(info);
        
        verify(newJedisOps).get(any());
        verify(oldJedisOps).get(any());
        verify(newJedisOps).set(any(), stringCaptor.capture());
        verify(oldJedisOps, never()).set(any(), any());
        
        RequestInfo setInfo = MAPPER.readValue(stringCaptor.getValue(), RequestInfo.class);
        assertEquals(USER_ID, setInfo.getUserId());
    }
    
    @Test
    public void updateRequestInfoNewCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();
        
        RequestInfo old = new RequestInfo.Builder().withUserId("oldUserId").build();
        when(newJedisOps.get(any())).thenReturn(MAPPER.writeValueAsString(old));
        
        cacheProvider.updateRequestInfo(info);
        
        verify(newJedisOps).get(any());
        verify(oldJedisOps, never()).get(any());
        verify(newJedisOps).set(any(), stringCaptor.capture());
        verify(oldJedisOps, never()).set(any(), any());
        
        RequestInfo setInfo = MAPPER.readValue(stringCaptor.getValue(), RequestInfo.class);
        assertEquals(USER_ID, setInfo.getUserId());
    }
    
    @Test
    public void removeRequestInfo() {
        cacheProvider.removeRequestInfo(USER_ID);
        
        verify(newJedisOps).del(REQUEST_INFO_KEY);
        verify(oldJedisOps).del(REQUEST_INFO_KEY);
    }
    
    @Test
    public void getRequestInfoNoCache() throws Exception {
        RequestInfo returned = cacheProvider.getRequestInfo(USER_ID);
        assertNull(returned);
        
        verify(oldJedisOps).get(REQUEST_INFO_KEY);
        verify(newJedisOps).get(REQUEST_INFO_KEY);
    }

    @Test
    public void getRequestInfoOldCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();
        when(oldJedisOps.get(REQUEST_INFO_KEY)).thenReturn(MAPPER.writeValueAsString(info));
        
        RequestInfo returned = cacheProvider.getRequestInfo(USER_ID);
        assertEquals(info, returned);
        
        verify(newJedisOps).get(REQUEST_INFO_KEY);
        verify(oldJedisOps).get(REQUEST_INFO_KEY);
    }
    
    @Test
    public void getRequestInfoNewCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId(USER_ID).build();
        when(newJedisOps.get(REQUEST_INFO_KEY)).thenReturn(MAPPER.writeValueAsString(info));
        
        RequestInfo returned = cacheProvider.getRequestInfo(USER_ID);
        assertEquals(info, returned);
        
        verify(newJedisOps).get(REQUEST_INFO_KEY);
        verify(oldJedisOps, never()).get(REQUEST_INFO_KEY);
    }
    
    @Test
    public void setUserSession() throws Exception {
        when(newJedisOps.ttl(USER_ID_SESSION_KEY)).thenReturn(20L);
        
        cacheProvider.setUserSession(session);
        
        verify(newJedisOps).ttl(USER_ID_SESSION_KEY);
        verify(newTransaction).setex(USER_ID_SESSION_KEY, 20, SESSION_TOKEN);
        verify(newTransaction).setex(eq(SESSION_TOKEN_KEY), eq(20), stringCaptor.capture());
        verify(newTransaction).exec();
        
        UserSession captured = MAPPER.readValue(stringCaptor.getValue(), UserSession.class);
        assertEquals(USER_ID, captured.getId());
    }
            
    private UserSession stubSession(String userId) {
        UserSession session = new UserSession();
        session.setSessionToken(SESSION_TOKEN);
        session.setParticipant(new StudyParticipant.Builder()
                .withId(userId)
                .withHealthCode("healthCode").build());
        return session;
    }

    @Test
    public void getUserSessionNoCache() {
        UserSession returned = cacheProvider.getUserSession(SESSION_TOKEN);
        assertNull(returned);
        
        verify(oldJedisOps).get(SESSION_TOKEN_KEY);
        verify(newJedisOps).get(SESSION_TOKEN_KEY);
    }

    @Test
    public void getUserSessionOldCache() throws Exception {
        UserSession session = stubSession(USER_ID);
        when(oldJedisOps.get(SESSION_TOKEN_KEY)).thenReturn(MAPPER.writeValueAsString(session));
        
        UserSession returned = cacheProvider.getUserSession(SESSION_TOKEN);
        assertEquals(USER_ID, returned.getId());
        
        verify(newJedisOps).get(SESSION_TOKEN_KEY);
        verify(oldJedisOps).get(SESSION_TOKEN_KEY);
    }
    
    @Test
    public void getUserSessionNewCache() throws Exception {
        UserSession session = stubSession(USER_ID);
        when(newJedisOps.get(SESSION_TOKEN_KEY)).thenReturn(MAPPER.writeValueAsString(session));
        
        UserSession returned = cacheProvider.getUserSession(SESSION_TOKEN);
        assertEquals(USER_ID, returned.getId());
        
        verify(newJedisOps).get(SESSION_TOKEN_KEY);
        verify(oldJedisOps, never()).get(SESSION_TOKEN_KEY);
    }
    
    @Test
    public void getUserSessionByUserIdNoCache() {
        UserSession returned = cacheProvider.getUserSessionByUserId(USER_ID);
        assertNull(returned);
        
        verify(newJedisOps).get(USER_ID_SESSION_KEY);
        verify(oldJedisOps).get(USER_ID_SESSION_KEY);
    }

    @Test
    public void getUserSessionByUserIdOldCache() throws Exception {
        UserSession session = stubSession(USER_ID);
        when(oldJedisOps.get(SESSION_TOKEN_KEY)).thenReturn(MAPPER.writeValueAsString(session));
        when(oldJedisOps.get(USER_ID_SESSION_KEY)).thenReturn(SESSION_TOKEN);
        
        UserSession returned = cacheProvider.getUserSessionByUserId(USER_ID);
        assertEquals(USER_ID, returned.getId());
        
        verify(newJedisOps).get(USER_ID_SESSION_KEY);
        verify(oldJedisOps).get(USER_ID_SESSION_KEY);
    }

    @Test
    public void getUserSessionByUserIdNewCache() throws Exception {
        UserSession session = stubSession(USER_ID);
        when(newJedisOps.get(SESSION_TOKEN_KEY)).thenReturn(MAPPER.writeValueAsString(session));
        when(newJedisOps.get(USER_ID_SESSION_KEY)).thenReturn(SESSION_TOKEN);

        UserSession returned = cacheProvider.getUserSessionByUserId(USER_ID);
        assertEquals(USER_ID, returned.getId());
        
        verify(newJedisOps).get(USER_ID_SESSION_KEY);
        verify(oldJedisOps, never()).get(USER_ID_SESSION_KEY);
    }

    @Test
    public void removeSession() {
        cacheProvider.removeSession(session);
        
        verify(newTransaction).del(SESSION_TOKEN_KEY);
        verify(newTransaction).del(USER_ID_SESSION_KEY);
        verify(oldTransaction).del(SESSION_TOKEN_KEY);
        verify(oldTransaction).del(USER_ID_SESSION_KEY);
    }

    @Test
    public void removeSessionByUserId() {
        when(newJedisOps.get(USER_ID_SESSION_KEY)).thenReturn(SESSION_TOKEN);
        when(oldJedisOps.get(USER_ID_SESSION_KEY)).thenReturn(SESSION_TOKEN);
        
        cacheProvider.removeSessionByUserId(USER_ID);
        
        verify(newTransaction).del(SESSION_TOKEN_KEY);
        verify(newTransaction).del(USER_ID_SESSION_KEY);
        verify(oldTransaction).del(SESSION_TOKEN_KEY);
        verify(oldTransaction).del(USER_ID_SESSION_KEY);
    }

    @Test
    public void setStudy() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        String ser = MAPPER.writeValueAsString(study);
        
        when(newJedisOps.setex(any(), anyInt(), any())).thenReturn("OK");
        
        cacheProvider.setStudy(study);
        
        verify(newJedisOps).setex(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser);
        verify(oldJedisOps, never()).setex(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser);
    }

    @Test
    public void getStudyNoCache() {
        cacheProvider.getStudy(STUDY_ID);
        
        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps).get(STUDY_ID_KEY);
        
        verify(newJedisOps, never()).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        verify(oldJedisOps, never()).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }
    
    @Test
    public void getStudyOldCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        String ser = MAPPER.writeValueAsString(study);
        
        when(oldJedisOps.get(STUDY_ID_KEY)).thenReturn(ser);
        
        Study returned = cacheProvider.getStudy(STUDY_ID);
        assertEquals(study, returned);
        
        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps).get(STUDY_ID_KEY);
        
        verify(newJedisOps, never()).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        verify(oldJedisOps).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }
    
    @Test
    public void getStudyNewCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        String ser = MAPPER.writeValueAsString(study);
        
        when(newJedisOps.get(STUDY_ID_KEY)).thenReturn(ser);
        
        Study returned = cacheProvider.getStudy(STUDY_ID);
        assertEquals(study, returned);
        
        verify(newJedisOps).get(STUDY_ID_KEY);
        verify(oldJedisOps, never()).get(STUDY_ID_KEY);

        verify(newJedisOps).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
        verify(oldJedisOps, never()).expire(STUDY_ID_KEY, BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS);
    }

    @Test
    public void removeStudy() {
        cacheProvider.removeStudy(STUDY_ID);
        
        verify(newJedisOps).del(STUDY_ID_KEY);
        verify(oldJedisOps).del(STUDY_ID_KEY);
    }

    @Test
    public void getStringNoCache() {
        cacheProvider.getString("testKey");
        
        verify(newJedisOps).get("testKey");
        verify(oldJedisOps).get("testKey");
    }

    @Test
    public void getStringOldCache() {
        when(oldJedisOps.get("testKey")).thenReturn("value");
        
        String returned = cacheProvider.getString("testKey");
        assertEquals("value", returned);
        
        verify(newJedisOps).get("testKey");
        verify(oldJedisOps).get("testKey");
    }

    @Test
    public void getStringNewCache() {
        when(newJedisOps.get("testKey")).thenReturn("value");
        
        String returned = cacheProvider.getString("testKey");
        assertEquals("value", returned);
        
        verify(newJedisOps).get("testKey");
        verify(oldJedisOps, never()).get("testKey");
    }

    @Test
    public void setString() {
        when(newJedisOps.setex("key", 10, "value")).thenReturn("OK");
        
        cacheProvider.setString("key", "value", 10);
        
        verify(newJedisOps).setex("key", 10, "value");
        verify(oldJedisOps, never()).setex("key", 10, "value");
    }
    
    @Test
    public void removeString() {
        cacheProvider.removeString("key");
        
        verify(newJedisOps).del("key");
        verify(oldJedisOps).del("key");
    }
}
