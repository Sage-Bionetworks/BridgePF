package org.sagebionetworks.bridge.play.interceptors;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;
import play.mvc.Http;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;

/**
 * This interceptor grabs the request ID from the request (see {@link RequestUtils#getRequestId}) and writes it to
 * ThreadLocal storage (see {@link BridgeUtils#getRequestId}, then un-sets it when the request is finished.
 */
@Component("requestInterceptor")
public class RequestInterceptor implements MethodInterceptor {
    
    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        // Set request ID in a request-scoped context object. This object will be replaced 
        // with further user-specific security information, if the controller method that 
        // was intercepted retrieves the user's session (this code is consolidated in the 
        // BaseController). For unauthenticated/public requests, we do *not* want a 
        // Bridge-Session header changing the security context of the call.
       
        Http.Request request = Http.Context.current().request();
        String requestId = RequestUtils.getRequestId(request);
        RequestContext.Builder builder = new RequestContext.Builder().withRequestId(requestId);
        BridgeUtils.setRequestContext(builder.build());

        // Proceed with method invocation.
        try {
            return method.proceed();
        } finally {
            // Clear request context when finished.
            BridgeUtils.setRequestContext(null);
        }
    }
}
