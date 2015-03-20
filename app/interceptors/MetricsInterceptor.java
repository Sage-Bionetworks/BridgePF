package interceptors;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.sagebionetworks.bridge.models.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import play.cache.Cache;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;

@Component("metricsInterceptor")
public class MetricsInterceptor implements MethodInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(MetricsInterceptor.class);

    /** Expires in the cache after 60 seconds. */
    private static final int EXPIRE = 60;

    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        final Metrics metrics = initMetrics();
        Cache.set(metrics.getCacheKey(), metrics, EXPIRE);
        try {
            final Result result = (Result)method.proceed();
            metrics.setStatus(result.toScala().header().status());
            return result;
        } finally {
            Cache.remove(metrics.getCacheKey());
            metrics.end();
            logger.info(metrics.toJsonString());
        }
    }

    Metrics initMetrics() {
        final Request request = Http.Context.current().request();
        final Metrics metrics = new Metrics(getRequestId(request));
        metrics.setMethod(request.method());
        metrics.setUri(request.path());
        metrics.setProtocol(request.version());
        metrics.setRemoteAddress(header(request, "X-Forwarded-For", request.remoteAddress()));
        metrics.setUserAgent(header(request, "User-Agent", null));
        return metrics;
    }

    private String getRequestId(final Request request) {
        return header(request, "X-Request-Id", "-");
    }

    private String header(final Request request, final String name, final String defaultVal) {
        String[] values = request.headers().get(name);
        return (values != null && values.length > 0) ? values[0] : defaultVal;
    }
}
