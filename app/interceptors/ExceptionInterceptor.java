package interceptors;

import java.util.Set;

import models.ExceptionMessage;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import play.libs.Json;
import play.mvc.Http;
import play.mvc.Results;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;

@Component
public class ExceptionInterceptor implements MethodInterceptor {

    private static Logger logger = LoggerFactory.getLogger(ExceptionInterceptor.class);
    
    private static Set<Class<? extends BridgeServiceException>> quietExceptions = Sets.newHashSet();
    static {
        quietExceptions.add(BadRequestException.class);
        quietExceptions.add(InvalidEntityException.class);
        quietExceptions.add(EntityAlreadyExistsException.class);
        quietExceptions.add(EntityNotFoundException.class);
        quietExceptions.add(UnauthorizedException.class);
        quietExceptions.add(NotAuthenticatedException.class);
    }
    
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
            throwable = Throwables.getRootCause(throwable);
            
            // Consent exceptions return a session payload (you are signed in),
            // but a 412 error status code.
            if (throwable instanceof ConsentRequiredException) {
                ConsentRequiredException cre = (ConsentRequiredException)throwable;
                UserSessionInfo session = new UserSessionInfo(cre.getUserSession());
                return Results.status(cre.getStatusCode(), Json.toJson(session));
            }
            
            if (!config.isLocal() && quietExceptions.contains(throwable.getClass())) {
                if (throwable instanceof InvalidEntityException) {
                    // For the near future, log this in the event it represents an error on our side.
                    Http.Request request = Http.Context.current().request();
                    String bodyContent = request.body().asText();
                    logger.debug(throwable.getMessage() + ": " + bodyContent, throwable);
                } else {
                    logger.debug(throwable.getMessage(), throwable);
                }
            } else {
                // stuff we don't expect, log the stacktrace at the error level
                logger.error(throwable.getMessage(), throwable);
            }
            
            int status = getStatusCode(throwable);
            String message = getMessage(throwable, status);
            
            ExceptionMessage exceptionMessage = new ExceptionMessage(throwable, message);
            return Results.status(status, Json.toJson(exceptionMessage));
        }
    }

    private int getStatusCode(Throwable throwable) {
        int status = 500;
        if (throwable instanceof BridgeServiceException) {
            status = ((BridgeServiceException)throwable).getStatusCode();
        }
        return status;
    }

    private String getMessage(Throwable throwable, int status) {
        String message = throwable.getMessage();
        if (StringUtils.isBlank(message)) {
            message = HttpStatus.getStatusText(status);
        }
        return message;
    }
}
