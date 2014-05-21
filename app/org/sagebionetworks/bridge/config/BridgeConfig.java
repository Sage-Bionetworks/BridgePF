package org.sagebionetworks.bridge.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.properties.EncryptableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BridgeConfig {

    private final Logger logger = LoggerFactory.getLogger(BridgeConfig.class);

    private static final String SYNAPSE_REPO_ENDPOINT =  "synapse.repo.endpoint";
    private static final String SYNAPSE_AUTH_ENDPOINT =  "synapse.auth.endpoint";
    private static final String SYNAPSE_FILE_ENDPOINT =  "synapse.file.endpoint";
    
    private static final String CONFIG_FILE = "bridge.conf";
    private static final String DEFAULT_CONFIG_FILE = "conf/" + CONFIG_FILE;
    private static final String USER_CONFIG_FILE = System.getProperty("user.home") + "/" + ".sbt" + "/" + CONFIG_FILE;

    // Property name for the user
    private static final String USER = "bridge.user";

    // Property name for the environment
    private static final String ENVIRONMENT = "bridge.env";

    // Property name for the encryption password
    private static final String PASSWORD = "bridge.pwd";
    // Property name for the encryption salt
    private static final String SALT = "bridge.salt";

    private final String user;
    private final Environment environment;
    private final Properties properties;

    // Reads the environment variables
    private final ConfigReader envReader = new ConfigReader() {
        @Override
        public String read(String name) {
            try {
                return System.getenv(name);
            } catch(SecurityException e) {
                logger.error("Cannot read environment variable " + name
                        + " because of SecurityException.");
                return null;
            }
        }
    };

    // Reads the command-line arguments
    private final ConfigReader argsReader = new ConfigReader() {
        @Override
        public String read(String name) {
            try {
                return System.getProperty(name);
            } catch(SecurityException e) {
                logger.error("Cannot read system property " + name
                        + " because of SecurityException.");
                return null;
            }
        }
    };

    BridgeConfig() {
        this(new File(DEFAULT_CONFIG_FILE));
    }

    BridgeConfig(File defaultConfig) {

        // Load default config
        final Properties properties = new Properties();
        try {
            loadProperties(new FileInputStream(defaultConfig), properties);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Missing default config at " + defaultConfig.getAbsolutePath());
        }

        // Load additional config from the user's sbt home
        // This overwrites properties of the same name in the default config
        File file = new File(USER_CONFIG_FILE);
        loadProperties(file, properties);

        final String user = read(USER, properties);
        if (user == null || user.isEmpty()) {
            throw new RuntimeException("Missing user. Please set '" + USER + "'");
        }
        this.user = user;

        // Find out the environment
        environment = readEnvironment(properties);
        if (environment == null) {
            throw new NullPointerException("Environment not set.");
        }

        // Collapse the properties for the current environment
        Properties collapsed = collapse(properties, environment.getEnvName());

        final String pwd = read(PASSWORD, properties);
        final String salt = read(SALT, properties);
        final StringEncryptor encryptor = EncryptorUtil.getEncryptor(pwd, salt);

        // Decryptable properties
        this.properties = new EncryptableProperties(collapsed, encryptor);
    }

    public String getUser() {
        return user;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public boolean isStub() {
        return Environment.STUB.equals(environment);
    }

    public boolean isLocal() {
        return Environment.LOCAL.equals(environment);
    }

    public boolean isDevelopment() {
        return Environment.DEV.equals(environment);
    }

    public boolean isProduction() {
        return Environment.PROD.equals(environment);
    }

    public String getProperty(String name) {
        return properties.getProperty(name);
    }
    
    public String getSynapseRepoEndpoint() {
        return getProperty(SYNAPSE_REPO_ENDPOINT);
    }

    public String getSynapseAuthEndpoint() {
        return getProperty(SYNAPSE_AUTH_ENDPOINT);
    }

    public String getSynapseFileEndpoint() {
        return getProperty(SYNAPSE_FILE_ENDPOINT);
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

    private Environment readEnvironment(final Properties properties) {
        final String envName = read(ENVIRONMENT, properties);
        if (envName == null) {
            logger.info("Environment not set. Is this the stub development?");
            return Environment.STUB;
        }
        for (Environment env : Environment.values()) {
            if (env.getEnvName().equals(envName)) {
                return env;
            };
        }
        throw new RuntimeException("Invalid environment " + envName + " from config.");
    }

    private String read(final String name, final Properties properties) {
        // First the command-line arguments (via System.getProperty())
        String value = argsReader.read(name);
        // Then the environment variables
        if (value == null) {
            value = envReader.read(name);
        }
        // Then the properties file
        if (value == null) {
            value = properties.getProperty(name);
        }
        return value;
    }

    /**
     * Collapses the properties into new properties relevant to the current environment.
     * Start with default properties. Overwrite with properties for the current environment
     * and properties read from the environment and the command-line.
     */
    private Properties collapse(final Properties properties, final String envName) {
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
            if (name.startsWith(envName + ".")) {
                String strippedName = name.substring(envName.length() + 1);
                collapsed.setProperty(strippedName, properties.getProperty(name));
            }
        }
        // Overwrite with command line arguments and environment variables
        for (Object key : collapsed.keySet()) {
            final String name = key.toString();
            String value = argsReader.read(name);
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
     * When the property is not bound to a particular environment.
     */
    private boolean isDefaultProperty(String propName) {
        for (Environment env : Environment.values()) {
            String envPrefix = env.getEnvName() + ".";
            if (propName.startsWith(envPrefix)) {
                return false;
            }
        }
        return true;
    }
}
