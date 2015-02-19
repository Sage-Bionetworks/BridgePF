package interceptors;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.mvc.Http;
import play.mvc.Result;

public class LoggingInterceptor implements MethodInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        Result result = (Result)method.proceed();
        if (logger.isInfoEnabled()) {
            // Common Log Format for access logging, minus the first part which 
            // is already part of our standard logging output.
            Http.Request request = Http.Context.current().request();
            
            String path = request.path();
            String verb = request.method();
            String version = request.version();
            String address = header(request, "X-Forwarded-For", request.remoteAddress());
            String requestId = header(request, "X-Request-Id", "-");
            String userAgent = header(request, "User-Agent", "-");
            
            play.mvc.Results.Status statusObj = (play.mvc.Results.Status)result;
            int status = statusObj.getWrappedSimpleResult().header().status();
            
            String output = String.format("%s [%s] \"%s %s %s\" %s %s", address, requestId, verb, path, version, status, userAgent);
            logger.info(output);
        }
        return result;
    }
    
    private String header(Http.Request request, String name, String defaultValue) {
        String[] values = request.headers().get(name);
        return (values != null && values.length > 0) ? values[0] : defaultValue;
    }
}
