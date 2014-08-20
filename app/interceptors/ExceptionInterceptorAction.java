package interceptors;

import models.ExceptionMessage;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.UserSessionInfo;

import com.google.common.base.Throwables;

import play.Logger;
import play.libs.F;
import play.libs.Json;
import play.libs.F.Promise;
import play.mvc.Action.Simple;
import play.mvc.Http.Context;
import play.mvc.Result;
import play.mvc.Results;
import play.mvc.SimpleResult;

public class ExceptionInterceptorAction extends Simple {

    @Override
    public Promise<SimpleResult> call(Context context) throws Throwable {
        try {
            return delegate.call(context);    
        } catch(Throwable throwable) {
            throwable = Throwables.getRootCause(throwable);
            
            // Consent exceptions return a session payload (you are signed in),
            // but a 412 error status code.
            if (throwable instanceof ConsentRequiredException) {
                ConsentRequiredException cre = (ConsentRequiredException)throwable;
                
                // This looks very dubious. For one thing, it could be in a cookie and that's okay.
                String sessionToken = context.request().getHeader("Bridge-Session");
                
                Result result = Results.status(cre.getStatusCode(), Json.toJson(new UserSessionInfo(cre.getUserSession())));
                return F.Promise.pure((SimpleResult)result);
            }

            // Don't log errors here. Log at the source with a level of detail that's useful for 
            // developers, at the correct level of severity.
            Logger.debug(throwable.getMessage(), throwable);
            
            int status = 500;
            if (throwable instanceof BridgeServiceException) {
                status = ((BridgeServiceException)throwable).getStatusCode();
            }
            String message = throwable.getMessage();
            if (StringUtils.isBlank(message)) {
                message = HttpStatus.getStatusText(status);
            }

            ExceptionMessage exceptionMessage = createMessagePayload(throwable, status, message);
            Result result = Results.status(status, Json.toJson(exceptionMessage));
            return F.Promise.pure((SimpleResult)result);
        }
    }
    
    private ExceptionMessage createMessagePayload(Throwable throwable, int status, String message) {
        return new ExceptionMessage(throwable, message);
    }

}
