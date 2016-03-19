package org.sagebionetworks.bridge.play.interceptors;

import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.METRICS_EXPIRE_SECONDS;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;

import java.util.regex.Pattern;

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
    
    private static final Pattern PARTICIPANTS_URL = Pattern.compile("(?<=(/participants/))([^/]*)");
    private static final String EMAIL_TOKEN = ":email";

    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        final Metrics metrics = initMetrics();
        Cache.set(metrics.getCacheKey(), metrics, METRICS_EXPIRE_SECONDS);
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
        final Metrics metrics = new Metrics(RequestUtils.getRequestId(request));
        // The only key we have for users is their email addresses, but it's sensitive. Don't log it.
        String uri = PARTICIPANTS_URL.matcher(request.path()).replaceAll(EMAIL_TOKEN);
        metrics.setMethod(request.method());
        metrics.setUri(uri);
        metrics.setProtocol(request.version());
        metrics.setRemoteAddress(RequestUtils.header(request, X_FORWARDED_FOR_HEADER, request.remoteAddress()));
        metrics.setUserAgent(RequestUtils.header(request, USER_AGENT, null));
        return metrics;
    }
}
