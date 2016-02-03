package org.sagebionetworks.bridge.play.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.test.Helpers.contentAsString;

import java.util.Map;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Maps;

import play.mvc.Http;
import play.mvc.Result;

public class ExceptionInterceptorTest {

    private ExceptionInterceptor interceptor;
    
    @Before
    public void before() throws Exception {
        interceptor = new ExceptionInterceptor();
        mockContext();
    }
    
    private Map<String,String[]> map(String[] values) {
        Map<String,String[]> map = Maps.newHashMap();
        for (int i=0; i <= values.length-2; i+=2) {
            map.put(values[i], new String[] { values[i+1] });
        }
        return map;
    }
    
    private void mockContext(String... values) throws Exception {
        Map<String,String[]> map = map(values);
        
        Http.Request request = mock(Http.Request.class);
        when(request.queryString()).thenReturn(map);
        
        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);

        Http.Context.current.set(context);
    }
    
    
    @Test
    public void consentRequiredSessionSerializedCorrectly() throws Throwable {
        User user = new User();
        user.setEmail("email@email.com");
        user.setFirstName("firstName");
        user.setLastName("lastName");
        user.setHealthCode("healthCode");
        user.setId("userId");
        user.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        user.setStudyKey("test");
        user.getDataGroups().add("group1");
        
        UserSession session = new UserSession();
        session.setAuthenticated(true);
        session.setEnvironment(Environment.DEV);
        session.setInternalSessionToken("internalToken");
        session.setSessionToken("sessionToken");
        session.setStudyIdentifier(new StudyIdentifierImpl("test"));
        session.setUser(user);
        
        ConsentRequiredException exception = new ConsentRequiredException(session);
        
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.proceed()).thenThrow(exception);
        
        Result result = (Result)interceptor.invoke(invocation);
        JsonNode node = new ObjectMapper().readTree(contentAsString(result));

        assertTrue(node.get("authenticated").asBoolean());
        assertFalse(node.get("consented").asBoolean());
        assertFalse(node.get("signedMostRecentConsent").asBoolean());
        assertEquals("all_qualified_researchers", node.get("sharingScope").asText());
        assertEquals("sessionToken", node.get("sessionToken").asText());
        assertEquals("develop", node.get("environment").asText());
        assertEquals("email@email.com", node.get("username").asText());
        assertTrue(node.get("dataSharing").asBoolean());
        assertEquals("UserSessionInfo", node.get("type").asText());
        ArrayNode array = (ArrayNode)node.get("roles");
        assertEquals(0, array.size());
        array = (ArrayNode)node.get("dataGroups");
        assertEquals(1, array.size());
        assertEquals("group1", array.get(0).asText());
        assertEquals(0, node.get("consentStatuses").size());
        // And no further properties
        assertEquals(12, node.size());
    }
}    
