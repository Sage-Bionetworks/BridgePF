package global;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import play.*;
import play.mvc.*;
import play.mvc.Http.*;
import play.libs.Json;
import play.libs.F.*;
import static play.mvc.Results.*;

public class Global extends GlobalSettings {

	private static ApplicationContext applicationContext;
	
	@Override
    public void onStart(Application application) {
        applicationContext = new ClassPathXmlApplicationContext("application-context.xml");
    }
	
	@Override
	public Promise<SimpleResult> onError(RequestHeader request, Throwable throwable) {
		// Apparently, this is the only hook for dealing with errors in the whole thing?
		// Hard to believe... for now everything must be a 500 error.
		
		StatusMessage message = new StatusMessage(500, throwable.getMessage());
		return Promise.<SimpleResult>pure(Results.status(500, Json.toJson(message)));
	}
	
	/* These don't work. Is it even possible to redirect like this in Play? 
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
