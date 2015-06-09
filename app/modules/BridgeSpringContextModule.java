package modules;

import java.util.Map;
import java.util.Map.Entry;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Controller;

import play.Logger;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.name.Names;

public class BridgeSpringContextModule extends AbstractModule {

    @Override
    protected void configure() {

        Logger.info("Environment: " + BridgeConfigFactory.getConfig().getEnvironment().name().toLowerCase());

        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");

        final AbstractApplicationContext bridgeAppContext = new ClassPathXmlApplicationContext("application-context.xml");
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                bridgeAppContext.close();
            }
        });
        Logger.info("Bridge Spring context loaded.");

        final Binder spBinder = binder();
        final BeanFactory beanFactory = bridgeAppContext.getBeanFactory();
        final Map<String, Controller> controllers = bridgeAppContext.getBeansOfType(Controller.class);
        for (Entry<String, Controller> entry: controllers.entrySet()) {
            final Class<?> clazz = entry.getValue().getClass();
            bindBean(spBinder, beanFactory, entry.getKey(), clazz);
            Logger.info(clazz.getName() + " is bound.");
        }
    }

    private <T> void bindBean(final Binder binder, final BeanFactory beanFactory,
            final String name, final Class<T> type) {
        binder.bind(type).annotatedWith(Names.named(name)).toInstance(
                type.cast(beanFactory.getBean(type.getSimpleName() + "Proxied")));
    }
}
