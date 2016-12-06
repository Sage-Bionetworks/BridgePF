package org.sagebionetworks.bridge.play.interceptors;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.springframework.stereotype.Component;

@Component("deprecationInterceptor")
public class DeprecationInterceptor implements MethodInterceptor {
    
    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        if (method.getMethod().isAnnotationPresent(Deprecated.class)) {
            BridgeUtils.addWarningMessage(BridgeConstants.BRIDGE_DEPRECATED_STATUS);
        }
        return method.proceed();
    }

}
