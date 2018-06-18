package org.sagebionetworks.bridge.play.interceptors;

import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

import play.mvc.Http;

@Component("staticHeadersInterceptor")
public class StaticHeadersInterceptor implements MethodInterceptor {
    
    public static Map<String,String> HEADERS = new ImmutableMap.Builder<String,String>()
            // Limits what a web browser will include or execute in a page; only applies to our html pages
            .put("Content-Security-Policy", "default-src 'self' 'unsafe-inline' assets.sagebridge.org")
            // Do not send a cookie across a connection that is not HTTPS. Leave this even though we've removed cookies
            .put("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
            // Do not allow Mime-Type content "sniffing," when we say something is JSON, it's JSON
            .put("X-Content-Type-Options", "nosniff")
            // Do not render our HTML pages in a frame, iframe or object
            .put("X-Frame-Options", "DENY")
            // Don't allow people to embed our PDFs in their web sites. May be overkill
            .put("X-Permitted-Cross-Domain-Policies", "none")
            // XSS protection (because we run inline scripts, this isn't a bad idea, but our page generation
            // is trivial and we have no 3rd party includes, so risk is very low)
            .put("X-XSS-Protection", "1; mode=block").build();
    
    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        Http.Response response = Http.Context.current().response();
        for (Map.Entry<String, String> entry : HEADERS.entrySet()) {
            response.setHeader(entry.getKey(), entry.getValue());    
        }
        return method.proceed();
    }

}
