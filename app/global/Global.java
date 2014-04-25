package global;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import play.*;

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
