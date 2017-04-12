package org.sagebionetworks.bridge.play.interceptors;

import java.util.Set;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.NoStackTraceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.amazonaws.AmazonServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;

import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;
import play.mvc.Results;

@Component("exceptionInterceptor")
public class ExceptionInterceptor implements MethodInterceptor {

    private final Logger logger = LoggerFactory.getLogger(ExceptionInterceptor.class);
    
    // We serialize exceptions to JSON, but do not want any of the root properties of Throwable 
    // to be exposed, so these are removed;
    private static final Set<String> UNEXPOSED_FIELD_NAMES = Sets.newHashSet("stackTrace", "localizedMessage",
            "suppressed", "cause", "errorType", "errorMessage", "retryable", "requestId", "serviceName", "httpHeaders",
            "errorCode", "rawResponse", "rawResponseContent");
    
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
        final Request request = Http.Context.current().request();
        final String requestId = RequestUtils.getRequestId(request);
        final String msg = "request: " + requestId + " " + throwable.getMessage();
        if (throwable.getClass().isAnnotationPresent(NoStackTraceException.class)) {
            logger.info(msg);
            return;
        }
        logger.error(msg, throwable);
    }

    private Result getResult(Throwable throwable) throws JsonProcessingException {
        // Consent exceptions return a session payload (you are signed in),
        // but a 412 error status code.
        if (throwable instanceof ConsentRequiredException) {
            ConsentRequiredException cre = (ConsentRequiredException)throwable;
            
            JsonNode info = UserSessionInfo.toJSON(cre.getUserSession());
            return Results.status(cre.getStatusCode(), info.toString());
        }
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(throwable);
        final int status = getStatusCode(throwable);
        final String message = getMessage(throwable, status);
        final String type = getType(throwable, node);
        
        node.put("message", message);
        node.put("type", type);
        node.remove(UNEXPOSED_FIELD_NAMES);
        
        return Results.status(status, node);
    }
    
    private String getType(final Throwable throwable, final ObjectNode node) {
        String type = node.get("type").asText();
        if (throwable instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException)throwable;
            if (ase.getStatusCode() >= 400 && ase.getStatusCode() < 500) {
                type = "BadRequestException";
            }
        }
        return type;
    }

    private int getStatusCode(final Throwable throwable) {
        int status = 500;
        if (throwable instanceof BridgeServiceException) {
            status = ((BridgeServiceException)throwable).getStatusCode();
        } else if (throwable instanceof AmazonServiceException) {
            status = ((AmazonServiceException)throwable).getStatusCode();
        }
        return status;
    }

    private String getMessage(final Throwable throwable, final int status) {
        String message = throwable.getMessage();
        if (throwable instanceof AmazonServiceException) {
            // This is a more appropriately formatted error message for end users than
            // amazonServiceException.getMessage()
            message = ((AmazonServiceException)throwable).getErrorMessage();
        }
        if (StringUtils.isBlank(message)) {
            message = Integer.toString(status);
        }
        return message;
    }
}
