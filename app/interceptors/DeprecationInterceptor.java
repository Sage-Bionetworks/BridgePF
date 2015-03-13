package interceptors;

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
            response.setHeader(BridgeConstants.BRIDGE_API_STATUS_HEADER, BridgeConstants.BRIDGE_DEPRECATED_STATUS);
        }
        return method.proceed();
    }

}
