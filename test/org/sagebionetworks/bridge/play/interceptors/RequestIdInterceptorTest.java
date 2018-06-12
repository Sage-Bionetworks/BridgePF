package org.sagebionetworks.bridge.play.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;

public class RequestIdInterceptorTest {
    private static final String REQUEST_ID = "request-id";

    @Test
    public void test() throws Throwable {
        // Mock Play headers. Note that we don't actually care about JSON body, but this helper method is the simplest
        // way to mock all things Play.
        Map<String, String[]> headerMap = ImmutableMap.of(BridgeConstants.X_REQUEST_ID_HEADER,
                new String[] { REQUEST_ID });
        TestUtils.mockPlayContextWithJson("{}", headerMap);

        // Mock method invocation.
        Object expectedReturnValue = new Object();
        MethodInvocation mockMethod = mock(MethodInvocation.class);
        when(mockMethod.proceed()).thenAnswer(invocation -> {
            // Verify that we set the request ID.
            assertEquals(REQUEST_ID, BridgeUtils.getRequestId());
            return expectedReturnValue;
        });

        // Execute.
        RequestIdInterceptor interceptor = new RequestIdInterceptor();
        Object returnValue = interceptor.invoke(mockMethod);
        assertSame(expectedReturnValue, returnValue);

        // Verify the method was _actually_ called.
        verify(mockMethod).proceed();

        // Verify we reset the request ID afterwards.
        assertNull(BridgeUtils.getRequestId());
    }
}