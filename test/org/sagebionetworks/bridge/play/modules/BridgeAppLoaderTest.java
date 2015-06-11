package org.sagebionetworks.bridge.play.modules;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import controllers.ApplicationController;
import play.ApplicationLoader.Context;
import play.Environment;
import play.inject.Injector;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;

public class BridgeAppLoaderTest {

    @Test
    public void test() {
        GuiceApplicationBuilder builder = new GuiceApplicationLoader().builder(
                new Context(Environment.simple())).overrides(new BridgeSpringContextModule());
        Injector injector = builder.build().injector();
        ApplicationController appController = injector.instanceOf(ApplicationController.class);
        assertNotNull("ApplicationController is initialized.", appController);
        assertNotNull("ApplicationController.studyService is injected.",
                ReflectionTestUtils.getField(appController, "studyService"));
    }
}
