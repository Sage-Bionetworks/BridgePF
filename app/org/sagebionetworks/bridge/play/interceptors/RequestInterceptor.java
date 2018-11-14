package org.sagebionetworks.bridge.play.interceptors;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import play.mvc.Http;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.accounts.UserSession;

/**
 * This interceptor grabs the request ID from the request (see {@link RequestUtils#getRequestId}) and writes it to
 * ThreadLocal storage (see {@link BridgeUtils#getRequestId}, then un-sets it when the request is finished.
 */
@Component("requestInterceptor")
public class RequestInterceptor implements MethodInterceptor {
    
    private CacheProvider cacheProvider;
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        // Set key security context values in a request-scoped context object
        Http.Request request = Http.Context.current().request();
        
        String requestId = RequestUtils.getRequestId(request);
        RequestContext.Builder builder = new RequestContext.Builder().withRequestId(requestId);
        
        String sessionToken = RequestUtils.getSessionToken(request);
        if (sessionToken != null) {
            UserSession session = cacheProvider.getUserSession(sessionToken);
            if (session != null) {
                builder.withCallerStudyId(session.getStudyIdentifier());
                builder.withCallerSubstudies(session.getParticipant().getSubstudyIds());
            }
        }
        BridgeUtils.setRequestContext(builder.build());

        // Proceed with method invocation.
        try {
            return method.proceed();
        } finally {
            // Unset request context when finished.
            BridgeUtils.setRequestContext(null);
        }
    }
}
