package global;

import static play.mvc.Results.badRequest;
import models.StatusMessage;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Http.RequestHeader;
import play.mvc.SimpleResult;

public class Global extends GlobalSettings {

    private static ApplicationContext applicationContext;

    @Override
    public void onStart(Application application) {
        String env = BridgeConfigFactory.getConfig().getEnvironment().name().toLowerCase();
        Logger.info("Environment: " + env);
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        applicationContext = new ClassPathXmlApplicationContext("application-context.xml");
    }

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
