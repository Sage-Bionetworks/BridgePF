package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    
    private CacheProvider cacheProvider;
    
    @Mock
    private JedisOps oldJedisOps;
    
    @Mock
    private JedisOps newJedisOps;
    
    @Mock
    private JedisTransaction transaction;
    
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    
    @Before
    public void before() {
        cacheProvider = new CacheProvider();
        cacheProvider.setBridgeObjectMapper(BridgeObjectMapper.get());
        cacheProvider.setJedisOps(oldJedisOps);
        cacheProvider.setNewJedisOps(newJedisOps);
        cacheProvider.setSessionExpireInSeconds(10);
    }
    
    @Test
    public void updateRequestInfoEmptyCache() {
        RequestInfo info = new RequestInfo.Builder().withUserId("userId").build();
        
        cacheProvider.updateRequestInfo(info);
        
        verify(newJedisOps).get(any());
        verify(oldJedisOps).get(any());
        verify(newJedisOps).set(any(), any());
        verify(oldJedisOps, never()).set(any(), any());
    }
    
    @Test
    public void updateRequestInfoOldCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId("userId").build();
        
        RequestInfo old = new RequestInfo.Builder().withUserId("oldUserId").build();
        when(oldJedisOps.get(any())).thenReturn(MAPPER.writeValueAsString(old));
        
        cacheProvider.updateRequestInfo(info);
        
        verify(newJedisOps).get(any());
        verify(oldJedisOps).get(any());
        verify(newJedisOps).set(any(), stringCaptor.capture());
        verify(oldJedisOps, never()).set(any(), any());
        
        RequestInfo setInfo = MAPPER.readValue(stringCaptor.getValue(), RequestInfo.class);
        assertEquals("userId", setInfo.getUserId());
    }
    
    @Test
    public void updateRequestInfoNewCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId("userId").build();
        
        RequestInfo old = new RequestInfo.Builder().withUserId("oldUserId").build();
        when(newJedisOps.get(any())).thenReturn(MAPPER.writeValueAsString(old));
        
        cacheProvider.updateRequestInfo(info);
        
        verify(newJedisOps).get(any());
        verify(oldJedisOps, never()).get(any());
        verify(newJedisOps).set(any(), stringCaptor.capture());
        verify(oldJedisOps, never()).set(any(), any());
        
        RequestInfo setInfo = MAPPER.readValue(stringCaptor.getValue(), RequestInfo.class);
        assertEquals("userId", setInfo.getUserId());
    }
    
    @Test
    public void removeRequestInfo() {
        cacheProvider.removeRequestInfo("userId");
        
        verify(newJedisOps).del("userId:request-info");
        verify(oldJedisOps).del("userId:request-info");
    }
    
    @Test
    public void getRequestInfoNoCache() throws Exception {
        RequestInfo returned = cacheProvider.getRequestInfo("test");
        assertNull(returned);
        
        verify(oldJedisOps).get("test:request-info");
        verify(newJedisOps).get("test:request-info");
    }

    @Test
    public void getRequestInfoOldCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId("userId").build();
        when(oldJedisOps.get("userId:request-info")).thenReturn(MAPPER.writeValueAsString(info));
        
        RequestInfo returned = cacheProvider.getRequestInfo("userId");
        assertEquals(info, returned);
        
        verify(newJedisOps).get("userId:request-info");
        verify(oldJedisOps).get("userId:request-info");
    }
    
    @Test
    public void getRequestInfoNewCache() throws Exception {
        RequestInfo info = new RequestInfo.Builder().withUserId("userId").build();
        when(newJedisOps.get("userId:request-info")).thenReturn(MAPPER.writeValueAsString(info));
        
        RequestInfo returned = cacheProvider.getRequestInfo("userId");
        assertEquals(info, returned);
        
        verify(newJedisOps).get("userId:request-info");
        verify(oldJedisOps, never()).get("userId:request-info");
    }
    
    @Test
    public void setUserSession() throws Exception {
        UserSession session = mockSessionAndTransaction(newJedisOps);
        
        cacheProvider.setUserSession(session);
        verify(transaction).setex("userId:session:user", 10, "sessionToken");
        verify(transaction).setex(eq("sessionToken:session"), eq(10), stringCaptor.capture());
        verify(transaction).exec();
        
        UserSession captured = MAPPER.readValue(stringCaptor.getValue(), UserSession.class);
        assertEquals("userId", captured.getId());
    }
            
    private UserSession mockSessionAndTransaction(JedisOps thisJedisOps) {
        UserSession session = stubSession("userId");
        
        when(thisJedisOps.getTransaction()).thenReturn(transaction);
        doReturn(transaction).when(transaction).setex(any(), anyInt(), any());
        
        when(thisJedisOps.getTransaction()).thenReturn(transaction);
        doReturn(transaction).when(transaction).del(any());
        
        return session;
    }
    
    private UserSession stubSession(String userId) {
        UserSession session = new UserSession();
        session.setSessionToken("sessionToken");
        session.setParticipant(new StudyParticipant.Builder()
                .withId(userId)
                .withHealthCode("healthCode").build());
        return session;
    }

    @Test
    public void getUserSessionNoCache() {
        UserSession returned = cacheProvider.getUserSession("sessionToken");
        assertNull(returned);
        
        verify(oldJedisOps).get("sessionToken:session");
        verify(newJedisOps).get("sessionToken:session");
    }

    @Test
    public void getUserSessionOldCache() throws Exception {
        UserSession session = stubSession("userId");
        when(oldJedisOps.get("sessionToken:session")).thenReturn(MAPPER.writeValueAsString(session));
        
        UserSession returned = cacheProvider.getUserSession("sessionToken");
        assertEquals("userId", returned.getId());
        
        verify(newJedisOps).get("sessionToken:session");
        verify(oldJedisOps).get("sessionToken:session");
    }
    
    @Test
    public void getUserSessionNewCache() throws Exception {
        UserSession session = stubSession("userId");
        when(newJedisOps.get("sessionToken:session")).thenReturn(MAPPER.writeValueAsString(session));
        
        UserSession returned = cacheProvider.getUserSession("sessionToken");
        assertEquals("userId", returned.getId());
        
        verify(newJedisOps).get("sessionToken:session");
        verify(oldJedisOps, never()).get("sessionToken:session");
    }
    
    @Test
    public void getUserSessionByUserIdNoCache() {
        UserSession returned = cacheProvider.getUserSessionByUserId("userId");
        assertNull(returned);
        
        verify(newJedisOps).get("userId:session:user");
        verify(oldJedisOps).get("userId:session:user");
    }

    @Test
    public void getUserSessionByUserIdOldCache() throws Exception {
        UserSession session = stubSession("userId");
        when(oldJedisOps.get("sessionToken:session")).thenReturn(MAPPER.writeValueAsString(session));
        when(oldJedisOps.get("userId:session:user")).thenReturn("sessionToken");
        
        UserSession returned = cacheProvider.getUserSessionByUserId("userId");
        assertEquals("userId", returned.getId());
        
        verify(newJedisOps).get("userId:session:user");
        verify(oldJedisOps).get("userId:session:user");
    }

    @Test
    public void getUserSessionByUserIdNewCache() throws Exception {
        UserSession session = stubSession("userId");
        when(newJedisOps.get("sessionToken:session")).thenReturn(MAPPER.writeValueAsString(session));
        when(newJedisOps.get("userId:session:user")).thenReturn("sessionToken");

        UserSession returned = cacheProvider.getUserSessionByUserId("userId");
        assertEquals("userId", returned.getId());
        
        verify(newJedisOps).get("userId:session:user");
        verify(oldJedisOps, never()).get("userId:session:user");
    }

    @Test
    public void removeSession() {
        mockSessionAndTransaction(newJedisOps);
        UserSession session = mockSessionAndTransaction(oldJedisOps);
        
        cacheProvider.removeSession(session);
        
        verify(transaction, times(2)).del("sessionToken:session");
        verify(transaction, times(2)).del("userId:session:user");
    }

    @Test
    public void removeSessionByUserId() {
        mockSessionAndTransaction(newJedisOps);
        mockSessionAndTransaction(oldJedisOps);
        
        when(newJedisOps.get("userId:session:user")).thenReturn("sessionToken");
        
        cacheProvider.removeSessionByUserId("userId");
        
        verify(transaction, times(2)).del("sessionToken:session");
        verify(transaction, times(2)).del("userId:session:user");
    }

    @Test
    public void setStudy() throws Exception {
        Study study = Study.create();
        study.setIdentifier("studyId");
        String ser = MAPPER.writeValueAsString(study);
        
        when(newJedisOps.setex(any(), anyInt(), any())).thenReturn("OK");
        
        cacheProvider.setStudy(study);
        
        verify(newJedisOps).setex("studyId:study", BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser);
        verify(oldJedisOps, never()).setex("studyId:study", BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, ser);
    }

    @Test
    public void getStudyNoCache() {
        cacheProvider.getStudy("studyId");
        
        verify(newJedisOps).get("studyId:study");
        verify(oldJedisOps).get("studyId:study");
    }
    
    @Test
    public void getStudyOldCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier("studyId");
        String ser = MAPPER.writeValueAsString(study);
        
        when(oldJedisOps.get("studyId:study")).thenReturn(ser);
        
        Study returned = cacheProvider.getStudy("studyId");
        assertEquals(study, returned);
        
        verify(newJedisOps).get("studyId:study");
        verify(oldJedisOps).get("studyId:study");
    }
    
    @Test
    public void getStudyNewCache() throws Exception {
        Study study = Study.create();
        study.setIdentifier("studyId");
        String ser = MAPPER.writeValueAsString(study);
        
        when(newJedisOps.get("studyId:study")).thenReturn(ser);
        
        Study returned = cacheProvider.getStudy("studyId");
        assertEquals(study, returned);
        
        verify(newJedisOps).get("studyId:study");
        verify(oldJedisOps, never()).get("studyId:study");
    }

    @Test
    public void removeStudy() {
        mockSessionAndTransaction(newJedisOps);
        mockSessionAndTransaction(oldJedisOps);

        cacheProvider.removeStudy("studyId");
        
        verify(newJedisOps).del("studyId:study");
        verify(oldJedisOps).del("studyId:study");
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
        mockSessionAndTransaction(newJedisOps);
        mockSessionAndTransaction(oldJedisOps);

        cacheProvider.removeString("key");
        
        verify(newJedisOps).del("key");
        verify(oldJedisOps).del("key");
    }
}
