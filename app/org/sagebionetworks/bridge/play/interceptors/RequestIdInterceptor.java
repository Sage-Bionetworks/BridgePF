package org.sagebionetworks.bridge.play.interceptors;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;
import play.mvc.Http;

import org.sagebionetworks.bridge.BridgeUtils;

/**
 * This interceptor grabs the request ID from the request (see {@link RequestUtils#getRequestId}) and writes it to
 * ThreadLocal storage (see {@link BridgeUtils#getRequestId}, then un-sets it when the request is finished.
 */
@Component("requestIdInterceptor")
public class RequestIdInterceptor implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        // Set request ID.
        Http.Request request = Http.Context.current().request();
        String requestId = RequestUtils.getRequestId(request);
        BridgeUtils.setRequestId(requestId);

        // Proceed with method invocation.
        try {
            return method.proceed();
        } finally {
            // Unset request Id when finished.
            BridgeUtils.setRequestId(null);
        }
    }
}
