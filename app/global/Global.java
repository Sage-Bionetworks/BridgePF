package global;

import java.io.File;

import models.StatusMessage;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import play.*;
import play.libs.Json;
import play.libs.F.Promise;
import play.mvc.SimpleResult;
import play.mvc.Http.RequestHeader;
import static play.mvc.Results.*;

public class Global extends GlobalSettings {

    private static ApplicationContext applicationContext;

    @Override
    public void onStart(Application application) {

        BridgeConfig bridgeConfig = new BridgeConfig();
        final String env = bridgeConfig.getEnvironment();
        Logger.info("Environment: " + env);



		String contextXml = env + "-application-context.xml";
        applicationContext = new ClassPathXmlApplicationContext(contextXml);
    }

	/* Don't work because the /* route handles all misses
	@Override
	public Promise<SimpleResult> onHandlerNotFound(RequestHeader header) {
		return Promise.<SimpleResult>pure(redirect("/404.html"));
	}
	
	@Override
	public Promise<SimpleResult> onError(RequestHeader request, Throwable throwable) {
		return Promise.<SimpleResult>pure(redirect("/500.html"));
	} */
	
	/**
	 * Must be handled in Global handler. It can happen during binding, before a controller is called.
	 */
	@Override
	public Promise<SimpleResult> onBadRequest(RequestHeader header, String message) {
	    return Promise.<SimpleResult>pure(badRequest(Json.toJson(new StatusMessage(message))));
	}
	
	@Override
    public <T> T getControllerInstance(Class<T> clazz) {
        if (applicationContext == null) {
            throw new IllegalStateException("application-context.xml is not initialized");
        }
        return applicationContext.getBean(clazz);
    }
	
}
