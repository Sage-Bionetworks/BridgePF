package org.sagebionetworks.bridge.play.interceptors;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.sagebionetworks.bridge.BridgeConstants;
import org.springframework.stereotype.Component;

import play.mvc.Http;

@Component("deprecationInterceptor")
public class DeprecationInterceptor implements MethodInterceptor {
    
    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        if (method.getMethod().isAnnotationPresent(Deprecated.class)) {
            Http.Response response = Http.Context.current().response();
            if (response.getHeaders().containsKey(BridgeConstants.BRIDGE_API_STATUS_HEADER)) {
                String previousWarning = response.getHeaders().get(BridgeConstants.BRIDGE_API_STATUS_HEADER);
                response.setHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER, previousWarning + "; " + BridgeConstants.BRIDGE_DEPRECATED_STATUS);
            } else {
                response.setHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER, BridgeConstants.BRIDGE_DEPRECATED_STATUS);
            }
        }
        return method.proceed();
    }

}
