package org.sagebionetworks.bridge.play.modules;

import java.util.Map;
import java.util.Map.Entry;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import play.Logger;
import play.mvc.Controller;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;

public class BridgeSpringContextModule extends AbstractModule {

    @Override
    protected void configure() {
        Logger.info("Environment: " + BridgeConfigFactory.getConfig().getEnvironment().name());
        loadDynamo();
        final AbstractApplicationContext appContext = loadAppContext();
        bindControllers(appContext);
    }

    private void loadDynamo() {
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        Logger.info("DynamoDB tables loaded.");
    }

    private AbstractApplicationContext loadAppContext() {
        final AbstractApplicationContext bridgeAppContext =
                new ClassPathXmlApplicationContext("application-context.xml");
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                bridgeAppContext.stop();
                bridgeAppContext.close();
            }
        });
        bridgeAppContext.start();
        Logger.info("Bridge Spring context loaded.");
        return bridgeAppContext;
    }

    private void bindControllers(final AbstractApplicationContext appContext) {
        final Binder spBinder = binder();
        final BeanFactory beanFactory = appContext.getBeanFactory();
        final Map<String, Controller> controllers = appContext.getBeansOfType(Controller.class);
        for (final Entry<String, Controller> entry : controllers.entrySet()) {
            final Class<?> clazz = entry.getValue().getClass();
            bindBean(spBinder, beanFactory, entry.getKey(), clazz);
        }
    }

    private <T> void bindBean(final Binder binder, final BeanFactory beanFactory,
            final String name, final Class<T> clazz) {
        if (!name.endsWith("Proxied")) {
            binder.bind(clazz).toInstance(clazz.cast(
                    beanFactory.getBean(clazz.getSimpleName() + "Proxied")));
        }
        Logger.info(clazz.getName() + " is bound.");
    }
}
