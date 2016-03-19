package org.sagebionetworks.bridge.play.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.play.interceptors.MetricsInterceptor;

import play.mvc.Http;
import play.mvc.Http.Context;
import play.mvc.Http.Request;

public class MetricsInterceptorTest {

    @Test
    public void testInitMetrics() throws Throwable {
        // Mock request
        final Request mockRequest = mock(Request.class);
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/v3/participants/test.user%2Btest20160301b%40sagebase.org/dataGroups", "/v3/participants/test.user%2Btest20160301b%40sagebase.org");
        when(mockRequest.version()).thenReturn("HTTP/1.1");
        final Map<String, String[]> headerMap = new HashMap<>();
        headerMap.put("X-Request-Id", new String[]{"12345"});
        headerMap.put("X-Forwarded-For", new String[]{"1.2.3.4"});
        headerMap.put("User-Agent", new String[]{"ifeng 6"});
        when(mockRequest.headers()).thenReturn(headerMap);
        // Mock context
        final Context mockContext = mock(Context.class);
        when(mockContext.request()).thenReturn(mockRequest);
        Http.Context.current = new ThreadLocal<Context>(){
            @Override
            protected Context initialValue() {
                return mockContext;
            }
        };
        // Test
        MetricsInterceptor interceptor = new MetricsInterceptor();
        Metrics metrics = interceptor.initMetrics();
        assertNotNull(metrics);
        assertEquals("12345:Metrics", metrics.getCacheKey());
        String json = metrics.toJsonString();
        assertNotNull(json);
        assertTrue(json.contains("\"version\":1"));
        assertTrue(json.contains("\"start\":"));
        assertTrue(json.contains("\"request_id\":\"12345\""));
        assertTrue(json.contains("\"method\":\"POST\""));
        assertTrue(json.contains("\"uri\":\"/v3/participants/:email/dataGroups\""));
        assertTrue(json.contains("\"protocol\":\"HTTP/1.1\""));
        assertTrue(json.contains("\"remote_address\":\"1.2.3.4\""));
        assertTrue(json.contains("\"user_agent\":\"ifeng 6\""));
        
        // Second variant of the URL is also correctly processed
        json = interceptor.initMetrics().toJsonString();
        assertTrue(json.contains("\"uri\":\"/v3/participants/:email\""));
    }
}
