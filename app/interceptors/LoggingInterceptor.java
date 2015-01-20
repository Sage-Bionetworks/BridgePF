package interceptors;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.mvc.Http;

public class LoggingInterceptor implements MethodInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        if (logger.isInfoEnabled()) {
            Http.Request request = Http.Context.current().request();
            String[] values = request.headers().get("User-Agent");
            String userAgent = (values == null || values.length == 0) ? "<NONE>" : values[0];
            logger.info("User-Agent: " + userAgent);
        }
        return method.proceed();
    }

}
