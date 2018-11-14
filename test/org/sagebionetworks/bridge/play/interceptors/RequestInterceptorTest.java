package org.sagebionetworks.bridge.play.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;

public class RequestInterceptorTest {
    private static final ImmutableSet<String> SUBSTUDIES = ImmutableSet.of("substudyA");
    private static final String REQUEST_ID = "request-id";

    @Test
    public void testWithNoSession() throws Throwable {
        assertRequestContext(null, (context) -> {
            assertEquals(REQUEST_ID, context.getId());
            assertNull(context.getCallerStudyId());
            assertNull(context.getCallerSubstudies());
        });
    }
    
    @Test
    public void testWithSession() throws Throwable {
        UserSession session = new UserSession(
                new StudyParticipant.Builder().withSubstudyIds(SUBSTUDIES).build());
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        
        assertRequestContext(session, (context) -> {
            assertEquals(REQUEST_ID, context.getId());
            assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, context.getCallerStudyId());
            assertEquals(SUBSTUDIES, context.getCallerSubstudies());
        });
    }
    
    // This doesn't happen... but just to ensure this interceptor is not brittle.
    @Test
    public void testWithEmptySession() throws Throwable {
        assertRequestContext(new UserSession(), (context) -> {
            assertEquals(REQUEST_ID, context.getId());
            assertNull(context.getCallerStudyId());
            assertEquals(ImmutableSet.of(), context.getCallerSubstudies());
        });
    }
    
    private void assertRequestContext(UserSession session, Consumer<RequestContext> consumer) throws Throwable {
        CacheProvider mockCacheProvider = mock(CacheProvider.class);
        when(mockCacheProvider.getUserSession("ABC-DEF")).thenReturn(session);
        
        Map<String, String[]> headerMap = ImmutableMap.of(BridgeConstants.X_REQUEST_ID_HEADER,
                new String[] { REQUEST_ID }, BridgeConstants.SESSION_TOKEN_HEADER, 
                new String[] { "ABC-DEF" });
        TestUtils.mockPlayContextWithJson("{}", headerMap);
        
        Object expectedReturnValue = new Object();
        MethodInvocation mockMethod = mock(MethodInvocation.class);
        when(mockMethod.proceed()).thenAnswer(invocation -> {
            RequestContext context = BridgeUtils.getRequestContext();
            consumer.accept(context); // verification
            return expectedReturnValue;
        });
        // Execute.
        RequestInterceptor interceptor = new RequestInterceptor();
        interceptor.setCacheProvider(mockCacheProvider);
        Object returnValue = interceptor.invoke(mockMethod);
        
        assertSame(expectedReturnValue, returnValue);

        // Verify the method was _actually_ called.
        verify(mockMethod).proceed();

        // Verify we reset the request ID afterwards.
        assertNull(BridgeUtils.getRequestContext());
    }
}