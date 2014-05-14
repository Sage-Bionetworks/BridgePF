package org.sagebionetworks.bridge.context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.properties.EncryptableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.api.Play;
import scala.Option;

public class BridgeConfig {

    private final Logger logger = LoggerFactory.getLogger(BridgeConfig.class);

    private static final String CONFIG_FILE = "bridge.conf";
    private static final String USER_CONFIG_FILE = System.getProperty("user.home") + "/" + ".sbt" + "/" + CONFIG_FILE;

    private static final String PASSWORD = "pwd";
    private static final String ENVIRONMENT = "env";
    private static final String ENV_LOCAL = "local";
    private static final String ENV_DEV = "dev";
    private static final String ENV_PROD = "prod";

    private final String environment;
    private final Properties properties;

    private final ConfigReader envReader = new ConfigReader() {
        @Override
        public String read(String name) {
            try {
                return System.getenv(name);
            } catch(SecurityException e) {
                logger.error("Cannot read environment variable " + name + " because of SecurityException.");
                return null;
            }
        }
    };

    private final ConfigReader cmdArgReader = new ConfigReader() {
        @Override
        public String read(String name) {
            try {
                return System.getProperty(name);
            } catch(SecurityException e) {
                logger.error("Cannot read system property " + name + " because of SecurityException.");
                return null;
            }
        }
    };

    public BridgeConfig() {

        // Load default config from source code
        final Properties properties = new Properties();
        Option<InputStream> config  = Play.resourceAsStream(CONFIG_FILE, Play.current());
        if (config.isEmpty()) {
            throw new RuntimeException("Missing brige config file " + CONFIG_FILE);
        }
        loadProperties(config.get(), properties);

        // Load additional config from the user's sbt home
        // This overwrites properties of the same name in the default config
        File file = new File(USER_CONFIG_FILE);
        loadProperties(file, properties);

        // Find out the environment
        environment = readEnvironment(properties);
        if (environment == null) {
            throw new NullPointerException("Environment undetermined.");
        }

        // Collapse the properties for the current environment
        Properties collapsed = collapse(properties, environment);

        final StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        // TODO: Better encryption
        // encryptor.setAlgorithm("alogrithm");
        // encryptor.setKeyObtentionIterations(1000);
        // encryptor.setSaltGenerator(saltGenerator);

        // Read the password for encryption/decryption
        final String pwd = read(PASSWORD, properties);
        if (pwd == null) {
            logger.warn("Missing encryptor/decryptor password.");
        } else {
            encryptor.setPassword(pwd);
        }

        // Decrypted properties
        this.properties = new EncryptableProperties(collapsed, encryptor);
	}

    public String getEnvironment() {
        return environment;
    }

    public boolean isLocal() {
        return ENV_LOCAL.equals(environment);
    }

    public boolean isDevelopment() {
        return ENV_DEV.equals(environment);
    }

    public boolean isProduction() {
        return ENV_PROD.equals(environment);
    }

    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    ///////////////////////////

    private void loadProperties(final InputStream inputStream, final Properties properties) {
        try {
            properties.load(inputStream);
            inputStream.close();
        } catch(IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                inputStream.close();
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void loadProperties(final File file, final Properties properties) {
        try {
            InputStream inputStream = new FileInputStream(file);
            loadProperties(inputStream, properties);
        } catch(FileNotFoundException e){
            logger.warn(file.getPath() + " not found and is skipped.");
        }
    }

    private String readEnvironment(final Properties properties) {
        final String env = read(ENVIRONMENT, properties);
        if (env == null) {
            logger.info("Environment not set. Is this local development?");
            return ENV_LOCAL;
        }
        if (!ENV_LOCAL.equals(env) && !ENV_DEV.equals(env) && !ENV_PROD.equals(env)) {
            throw new RuntimeException("Invalid environment " + env + " from config.");
        }
        return env;
    }

    private String read(final String name, final Properties properties) {
        // First command line argument (System.getProperty())
        String value = cmdArgReader.read(name);
        // Then environment variable
        if (value == null) {
            value = envReader.read(name);
        }
        // Then properties file
        if (value == null) {
            value = properties.getProperty(name);
        }
        return value;
    }

    /**
     * Collapses the properties into new properties relevant to the current environment.
     * Start with default properties. Overwrite with properties for the current environment
     * and properties read from the environment and the command line.
     */
    private Properties collapse(final Properties properties, final String environment) {
        Properties collapsed = new Properties();
        // Read the default properties
        for (Object key : properties.keySet()) {
            final String name = key.toString();
            if (isDefaultProperty(name)) {
                collapsed.setProperty(name, properties.getProperty(name));
            }
        }
        // Overwrite with properties for the current environment
        for (Object key : properties.keySet()) {
            final String name = key.toString();
            if (name.startsWith(environment + ".")) {
                String strippedName = name.substring(environment.length() + 1);
                collapsed.setProperty(strippedName, properties.getProperty(name));
            }
        }
        // Overwrite with command line arguments and environment variables
        for (Object key : collapsed.keySet()) {
            final String name = key.toString();
            String value = cmdArgReader.read(name);
            if (value == null) {
                value = envReader.read(name);
            }
            if (value != null) {
                collapsed.setProperty(name, value);
            }
        }
        return collapsed;
    }

    /**
     * If the property is not bound to a particular environment.
     */
    private boolean isDefaultProperty(String name) {
        if (name.startsWith(ENV_LOCAL + ".")) {
            return false;
        }
        if (name.startsWith(ENV_DEV + ".")) {
            return false;
        }
        if (name.startsWith(ENV_PROD + ".")) {
            return false;
        }
        return true;
    }
}
