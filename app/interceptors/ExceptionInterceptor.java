package interceptors;

import models.ExceptionMessage;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.NoStackTraceException;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;

@Component("exceptionInterceptor")
public class ExceptionInterceptor implements MethodInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionInterceptor.class);

    private BridgeConfig config;

    @Autowired
    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }

    @Override
    public Object invoke(MethodInvocation method) throws Throwable {
        try {
            return method.proceed();
        } catch(Throwable throwable) {
            logException(throwable);
            return getResult(throwable);
        }
    }

    private void logException(final Throwable throwable) {
        final boolean noStackTrace = throwable.getClass().isAnnotationPresent(NoStackTraceException.class);
        if (noStackTrace && config.isLocal()) {
            logger.debug(throwable.getMessage(), throwable);
            return;
        }
        if (noStackTrace) {
            logger.debug(throwable.getMessage());
            return;
        }
        logger.error(throwable.getMessage(), throwable);
    }

    private Result getResult(final Throwable throwable) {
        final int status = getStatusCode(throwable);
        // Consent exceptions return a session payload (you are signed in),
        // but a 412 error status code.
        if (throwable instanceof ConsentRequiredException) {
            ConsentRequiredException cre = (ConsentRequiredException)throwable;
            UserSessionInfo session = new UserSessionInfo(cre.getUserSession());
            return Results.status(cre.getStatusCode(), Json.toJson(session));
        }
        String message = getMessage(throwable, status);
        final ExceptionMessage exceptionMessage = new ExceptionMessage(throwable, message);
        return Results.status(status, Json.toJson(exceptionMessage));
    }

    private int getStatusCode(final Throwable throwable) {
        int status = 500;
        if (throwable instanceof BridgeServiceException) {
            status = ((BridgeServiceException)throwable).getStatusCode();
        }
        return status;
    }

    private String getMessage(final Throwable throwable, final int status) {
        String message = throwable.getMessage();
        if (StringUtils.isBlank(message)) {
            message = HttpStatus.getStatusText(status);
        }
        return message;
    }
}
