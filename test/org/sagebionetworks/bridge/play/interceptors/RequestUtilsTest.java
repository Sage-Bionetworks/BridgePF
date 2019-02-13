package org.sagebionetworks.bridge.play.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.play.interceptors.RequestUtils;

import play.mvc.Http.Request;

public class RequestUtilsTest {

    @Test
    public void test() throws Exception {
        final Request mockRequest = mock(Request.class);
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/v3/test");
        when(mockRequest.version()).thenReturn("HTTP/1.1");
        
        TestUtils.mockPlay().withRequest(mockRequest)
            .withHeader("X-Request-Id", "123")
            .withHeader("User-Agent", "ifeng 6")
            .withHeader("Bridge-Session", "ABC-DEF").mock();
        
        assertEquals("123", RequestUtils.getRequestId(mockRequest));
        assertEquals("ABC-DEF", RequestUtils.getSessionToken(mockRequest));
        assertEquals("ifeng 6", RequestUtils.header(mockRequest, "User-Agent", null));
        assertEquals("ABC-DEF", RequestUtils.header(mockRequest, "Bridge-Session", null));
    }
    
    @Test
    public void testUserAgentOnly() throws Exception {
        final Request mockRequest = mock(Request.class);
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/v3/test");
        when(mockRequest.version()).thenReturn("HTTP/1.1");
        
        TestUtils.mockPlay().withRequest(mockRequest).withHeader("User-Agent", "ifeng 6").mock();
        
        assertFalse("123".equals(RequestUtils.getRequestId(mockRequest)));
        assertFalse("ABC-DEF".equals(RequestUtils.getSessionToken(mockRequest)));
    }
    
    @Test 
    public void testNonUserAgentHeadersOnly() throws Exception {
        final Request mockRequest = mock(Request.class);
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/v3/test");
        when(mockRequest.version()).thenReturn("HTTP/1.1");
        
        TestUtils.mockPlay().withRequest(mockRequest)
            .withHeader("X-Request-Id", "123")
            .withHeader("Bridge-Session", "ABC-DEF").mock();
        
        assertNull(RequestUtils.header(mockRequest, "User-Agent", null));
    }
}