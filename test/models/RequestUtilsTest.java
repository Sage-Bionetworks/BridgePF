package models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import play.mvc.Http.Request;

public class RequestUtilsTest {

    @Test
    public void test() {
        final Request mockRequest = mock(Request.class);
        when(mockRequest.method()).thenReturn("POST");
        when(mockRequest.path()).thenReturn("/api/v1/test");
        when(mockRequest.version()).thenReturn("HTTP/1.1");
        final Map<String, String[]> headerMap = new HashMap<>();
        headerMap.put("X-Request-Id", new String[]{"123", "789"});
        headerMap.put("User-Agent", new String[]{"ifeng 6"});
        when(mockRequest.headers()).thenReturn(headerMap);
        assertEquals("123", RequestUtils.getRequestId(mockRequest));
        assertEquals("ifeng 6", RequestUtils.header(mockRequest, "User-Agent", null));
        headerMap.remove("X-Request-Id");
        assertFalse("123".equals(RequestUtils.getRequestId(mockRequest)));
        assertNotNull(RequestUtils.getRequestId(mockRequest));
        headerMap.remove("User-Agent");
        assertNull(RequestUtils.header(mockRequest, "User-Agent", null));
    }
}
