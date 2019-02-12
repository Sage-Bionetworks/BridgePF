package org.sagebionetworks.bridge.play.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;


import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.accounts.UserSession;

public class RequestInterceptorTest {
    private static final String REQUEST_ID = "request-id";

    @Test
    public void testWithNoSession() throws Throwable {
        assertRequestContext(null, (context) -> {
            assertEquals(REQUEST_ID, context.getId());
        });
    }
    
    private void assertRequestContext(UserSession session, Consumer<RequestContext> consumer) throws Throwable {
        CacheProvider mockCacheProvider = mock(CacheProvider.class);
        when(mockCacheProvider.getUserSession("ABC-DEF")).thenReturn(session);
        
        TestUtils.mockPlay().withJsonBody("{}")
            .withHeader(BridgeConstants.X_REQUEST_ID_HEADER, REQUEST_ID).mock();
        
        Object expectedReturnValue = new Object();
        MethodInvocation mockMethod = mock(MethodInvocation.class);
        when(mockMethod.proceed()).thenAnswer(invocation -> {
            RequestContext context = BridgeUtils.getRequestContext();
            consumer.accept(context); // verification
            return expectedReturnValue;
        });
        // Execute.
        RequestInterceptor interceptor = new RequestInterceptor();
        Object returnValue = interceptor.invoke(mockMethod);
        
        assertSame(expectedReturnValue, returnValue);

        // Verify the method was _actually_ called.
        verify(mockMethod).proceed();

        // Verify we reset the request ID afterwards.
        assertEquals(RequestContext.NULL_INSTANCE, BridgeUtils.getRequestContext());
    }
}