package global;

import models.ExceptionMessage;
import models.JsonPayload;
import models.StatusMessage;

import org.sagebionetworks.client.exceptions.SynapseServerException;
import org.sagebionetworks.repo.model.TermsOfUseException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.google.common.base.Throwables;

import play.*;
import play.mvc.*;
import play.mvc.Http.*;
import play.libs.Json;
import play.libs.F.*;

public class Global extends GlobalSettings {

	private static ApplicationContext applicationContext;
	
	@Override
    public void onStart(Application application) {
        applicationContext = new ClassPathXmlApplicationContext("application-context.xml");
        
        // If the context has been wired to use a stub, then we populate the stub with some
        // user accounts, etc. so we can manipulate the application. This needs to be 
        // configurable in a manner that probably involves:
        // http://stackoverflow.com/questions/17193795/how-to-add-environment-profile-config-to-sbt/20573422#20573422
        /*
        try {
            StubOnStartupHandler handler = new StubOnStartupHandler();
            handler.stub(applicationContext);
        } catch(Throwable throwable) {
        	throw new RuntimeException(throwable);
        }
        */
    }
	
	@Override
	public Promise<SimpleResult> onError(RequestHeader request, Throwable throwable) {
		// This is the only hook for dealing with errors that I can find in Play.
		// The biggest issue with this is that we can't adjust the status appropriately.
		throwable = Throwables.getRootCause(throwable);

		int status = 500;
		if (throwable instanceof SynapseServerException) {
			status = ((SynapseServerException)throwable).getStatusCode();
		} else if (throwable instanceof TermsOfUseException) {
			status = 412; // "Precondition Failed"
		}
		
		JsonPayload<?> message = null;
		if (throwable.getMessage() != null) {
			message = new ExceptionMessage(throwable);
		} else {
			message = new StatusMessage("ServerError", "There has been a server error. We cannot fulfill your request at this time.");
		}
		return Promise.<SimpleResult>pure(Results.status(status, Json.toJson(message)));
	}
	
	/* These don't work. Is it possible to redirect like this in Play? 
	@Override
	public Promise<SimpleResult> onHandlerNotFound(RequestHeader header) {
		//return Promise.<SimpleResult>pure(null);
		return Promise.<SimpleResult>pure(redirect("/404.html"));
	}
	
	@Override
	public Promise<SimpleResult> onError(RequestHeader request, Throwable throwable) {
		return Promise.<SimpleResult>pure(redirect("/500.html"));
	}
	*/
	
	@Override
    public <T> T getControllerInstance(Class<T> clazz) {
        if (applicationContext == null) {
            throw new IllegalStateException("application-context.xml is not initialized");
        }
        return applicationContext.getBean(clazz);
    }
	
}
