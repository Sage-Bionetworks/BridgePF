package interceptors;

import models.ExceptionMessage;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.libs.Json;
import play.mvc.Results;

import com.google.common.base.Throwables;

public class ExceptionInterceptor implements MethodInterceptor {

    private static Logger logger = LoggerFactory.getLogger(ExceptionInterceptor.class);
    
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
                return Results.status(cre.getStatusCode(), Json.toJson(new UserSessionInfo(cre.getUserSession())));
            } 

            // Don't log errors here. Log at the source with a level of detail that's useful for 
            // developers, at the correct level of severity. That said, there are times when we 
            // want to see an exception while developing and it is just rethrown or thrown out.
            logger.debug(throwable.getMessage(), throwable);
            
            int status = 500;
            if (throwable instanceof BridgeServiceException) {
                status = ((BridgeServiceException)throwable).getStatusCode();
            }
            String message = throwable.getMessage();
            if (StringUtils.isBlank(message)) {
                message = HttpStatus.getStatusText(status);
            }
            
            ExceptionMessage exceptionMessage = new ExceptionMessage(throwable, message);
            return Results.status(status, Json.toJson(exceptionMessage));
        }
    }
}
