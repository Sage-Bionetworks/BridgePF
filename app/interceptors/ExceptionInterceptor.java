package interceptors;

import models.ExceptionMessage;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.client.exceptions.SynapseServerException;

import play.Logger;
import play.libs.Json;
import play.mvc.Results;

import com.google.common.base.Throwables;

public class ExceptionInterceptor implements MethodInterceptor {
	
	@Override
	public Object invoke(MethodInvocation method) throws Throwable {
		try {
			return method.proceed();
		} catch(Throwable throwable) {
			
			throwable = Throwables.getRootCause(throwable);
			Logger.error(throwable.getMessage(), throwable);

			int status = 500;
			if (throwable instanceof SynapseServerException) {
				status = ((SynapseServerException)throwable).getStatusCode();
			} else if (throwable instanceof BridgeServiceException) {
			    status = ((BridgeServiceException)throwable).getStatusCode();
			}
			String message = throwable.getMessage();
			if (StringUtils.isBlank(message)) {
			    message = HttpStatus.getStatusText(status);
			}

			ExceptionMessage exceptionMessage = createMessagePayload(throwable, status, message);
			return Results.status(status, Json.toJson(exceptionMessage));
		}
	}
	
	private ExceptionMessage createMessagePayload(Throwable throwable, int status, String message) {
	    if (throwable instanceof ConsentRequiredException) {
            return new ExceptionMessage(throwable, message, ((ConsentRequiredException) throwable).getSessionToken());
	    }
		return new ExceptionMessage(throwable, message);
	}

}
