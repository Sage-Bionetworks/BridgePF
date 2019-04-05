package org.sagebionetworks.bridge.exceptions;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static play.test.Helpers.contentAsString;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.play.interceptors.ExceptionInterceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.mvc.Result;

public class AuthenticationFailedExceptionTest {
    @Test
    public void serializesCorrectly() throws Throwable {
        ExceptionInterceptor interceptor = spy(ExceptionInterceptor.class);
        AuthenticationFailedException e = new AuthenticationFailedException();
        TestUtils.mockPlay().mock();
        
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.proceed()).thenThrow(e);
        
        Result result = (Result)interceptor.invoke(invocation);
        JsonNode node = new ObjectMapper().readTree(contentAsString(result));
        
        assertEquals(401, node.get("statusCode").asInt());
        assertEquals("Authentication failed.", node.get("message").asText());
        assertEquals("AuthenticationFailedException", node.get("type").asText());
        assertEquals(3, node.size());
    }
}
