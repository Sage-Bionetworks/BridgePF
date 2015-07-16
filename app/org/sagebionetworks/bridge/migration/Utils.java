package org.sagebionetworks.bridge.migration;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

final class Utils {

    static AbstractApplicationContext loadAppContext() {
        final AbstractApplicationContext appContext =
                new ClassPathXmlApplicationContext("application-context.xml");
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                appContext.stop();
                appContext.close();
            }
        });
        return appContext;
    }

    static void checkEnvironmentUser(final Environment env, final String user) {
        checkNotNull(env);
        checkNotNull(user);
        final BridgeConfig config = BridgeConfigFactory.getConfig();
        if (!config.getEnvironment().equals(env)) {
            throw new RuntimeException("Wrong environment. Expected environment: " + env.name()
                    + ". Actual environment: " + config.getEnvironment() + ".");
        }
        if (!config.getUser().equals(user)) {
            throw new RuntimeException("Wrong user. Expected user: " + user
                    + ". Actual user: " + config.getUser() + ".");
        }
    }

    static void checkStudy(final AbstractApplicationContext appContext, final String studyId) {
        final StudyService studyService = appContext.getBean(StudyService.class);
        final Study study = studyService.getStudy(studyId);
        if (study == null) {
            throw new RuntimeException("Study " + study + " does not exist.");
        }
    }
}
